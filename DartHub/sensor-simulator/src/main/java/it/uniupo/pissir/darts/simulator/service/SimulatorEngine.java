package it.uniupo.pissir.darts.simulator.service;

import it.uniupo.pissir.darts.simulator.generator.DartSector;
import it.uniupo.pissir.darts.simulator.generator.GeneratedThrow;
import it.uniupo.pissir.darts.simulator.generator.MatchScenarioGenerator;
import it.uniupo.pissir.darts.simulator.generator.Multiplier;
import it.uniupo.pissir.darts.simulator.generator.Scenario;
import it.uniupo.pissir.darts.simulator.generator.ThrowEventGenerator;
import it.uniupo.pissir.darts.simulator.publisher.SensorMqttPublisher;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Genera i tiri del sensore simulato secondo lo scenario selezionato. 
 */
public final class SimulatorEngine {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorEngine.class);
    private static final long INTERVALLO_STATO_MS = 10_000;

    private static final long INTERVALLO_MINIMO_MS = 50;
    private static final List<GeneratedThrow> SEQUENZA_FISSA = costruisciSequenza();

    private final SensorMqttPublisher publisher;
    private final Long seed;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "simulator-engine");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ScheduledFuture<?> tickFuture;
    private volatile long intervalloMs;
    private volatile Scenario scenario;
    private volatile int batteria = 100;

    private ThrowEventGenerator generatoreCasuale;
    private MatchScenarioGenerator generatorePartita;
    private int indiceSequenza;

    private volatile Consumer<GeneratedThrow> onTiro = tiro -> { };
    private volatile Runnable onScenarioConcluso = () -> { };
    private volatile LongConsumer onRiconnesso = secondi -> { };

    /**
     * Inizializza {@code SimulatorEngine} con i dati e le dipendenze indicati.
     *
     * @param publisher publisher da utilizzare
     * @param intervalloMs intervallo fra i tiri in millisecondi
     * @param scenario scenario di generazione dei tiri
     * @param seed seme del generatore casuale
     * @see it.uniupo.pissir.darts.simulator.publisher.SensorMqttPublisher
     *  
     */
    public SimulatorEngine(SensorMqttPublisher publisher, long intervalloMs, Scenario scenario, Long seed) {
        this.publisher = publisher;
        publisher.setOnMatchChanged(this::ferma);
        this.intervalloMs = validaIntervallo(intervalloMs);
        this.scenario = scenario;
        this.seed = seed;
        reinizializzaGeneratori();
        executor.scheduleAtFixedRate(this::pubblicaStato, 0, INTERVALLO_STATO_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Imposta su tiro per il componente.
     *
     * @param callback funzione da richiamare al verificarsi dell'evento
     * @see java.util.function.Consumer
     *  
     */
    public void setOnTiro(Consumer<GeneratedThrow> callback) {
        this.onTiro = callback;
    }

    /**
     * Imposta su scenario concluso per il componente.
     *
     * @param callback funzione da richiamare al verificarsi dell'evento
     *  
     */
    public void setOnScenarioConcluso(Runnable callback) {
        this.onScenarioConcluso = callback;
    }

    /**
     * Imposta su riconnesso per il componente.
     *
     * @param callback funzione da richiamare al verificarsi dell'evento
     * @see java.util.function.LongConsumer
     *  
     */
    public void setOnRiconnesso(LongConsumer callback) {
        this.onRiconnesso = callback;
    }

    /**
     * Avvia l'elaborazione del componente.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public synchronized boolean avvia() {
        if (tickFuture != null && !tickFuture.isDone()) {
            return false;
        }
        tickFuture = executor.scheduleAtFixedRate(this::tick, 0, intervalloMs, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Ferma l'attività del componente.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public synchronized boolean ferma() {
        if (tickFuture == null) {
            return false;
        }
        tickFuture.cancel(false);
        tickFuture = null;
        return true;
    }

    /**
     * Cambia velocita per il simulator engine.
     *
     * @param nuovoIntervalloMs nuovo intervallo fra i tiri in millisecondi
     *  
     */
    public synchronized void cambiaVelocita(long nuovoIntervalloMs) {
        intervalloMs = validaIntervallo(nuovoIntervalloMs);
        if (tickFuture != null) {
            ferma();
            avvia();
        }
    }

    /**
     * Valida intervallo per il simulator engine.
     *
     * @param intervalloMs intervallo fra i tiri in millisecondi
     * @return il valore numerico calcolato o letto
     * @throws ValidationException se i dati o lo stato del componente non consentono l'operazione
     *  
     */
    private static long validaIntervallo(long intervalloMs) {
        if (intervalloMs < INTERVALLO_MINIMO_MS) {
            throw new ValidationException(
                    "intervalloMs deve essere almeno " + INTERVALLO_MINIMO_MS + " (valori più bassi rischiano di "
                            + "sovraccaricare il broker MQTT condiviso con il resto della piattaforma)");
        }
        return intervalloMs;
    }

    /**
     * Cambia scenario per il simulator engine.
     *
     * @param nuovo nuovo da utilizzare
     * @see it.uniupo.pissir.darts.simulator.generator.Scenario
     *  
     */
    public synchronized void cambiaScenario(Scenario nuovo) {
        scenario = nuovo;
        reinizializzaGeneratori();
    }

    /**
     * Invia un tiro scelto esplicitamente dall'utente.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @return l'oggetto {@link it.uniupo.pissir.darts.simulator.generator.GeneratedThrow} prodotto o recuperato
     * @throws IllegalStateException se viene rilevato l'errore «Sensore disconnesso: tiro non inviato»
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    public GeneratedThrow tiroManuale(DartSector settore, Multiplier moltiplicatore) {
        GeneratedThrow tiro = new GeneratedThrow(settore, moltiplicatore);
        if (!publisher.publishThrow(tiro)) {
            throw new IllegalStateException("Sensore disconnesso: tiro non inviato");
        }
        return tiro;
    }

    /**
     * Interrompe la connessione MQTT del sensore.
     *
     * @param secondi intervallo in secondi
     * @throws MqttException se l'operazione non può essere completata
     *  
     */
    public void disconnetti(long secondi) throws MqttException {
        publisher.disconnect();
        executor.schedule(() -> {
            try {
                publisher.reconnect();
                onRiconnesso.accept(secondi);
            } catch (MqttException e) {
                LOG.error("Riconnessione fallita", e);
            }
        }, secondi, TimeUnit.SECONDS);
    }

    /**
     * Legge lo stato corrente della partita o del componente.
     *
     * @return stato corrente
     * @see it.uniupo.pissir.darts.simulator.service.SimulatorStato
     *  
     */
    public synchronized SimulatorStato statoCorrente() {
        return new SimulatorStato(publisher.localeId(), publisher.dartboardId(), scenario, intervalloMs,
                tickFuture != null && !tickFuture.isDone(), publisher.isConnected(), batteria,
                publisher.activeMatchId());
    }

    /**
     * Ferma l'attività e chiude le risorse del componente. 
     */
    public synchronized void chiudi() {
        ferma();
        executor.shutdownNow();
    }

    /**
     * Esegue un passo della generazione automatica dei tiri. 
     */
    private synchronized void tick() {
        GeneratedThrow tiro = prossimoTiroSecondoScenario();
        if (tiro == null) {
            ferma();
            onScenarioConcluso.run();
            return;
        }
        publisher.publishThrow(tiro);
        onTiro.accept(tiro);
    }

    /**
     * Genera il tiro successivo in base allo scenario selezionato.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.simulator.generator.GeneratedThrow} prodotto o recuperato
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    private GeneratedThrow prossimoTiroSecondoScenario() {
        return switch (scenario) {
            case RANDOM -> generatoreCasuale.generate();
            case PARTITA_501 -> generatorePartita.prossimoTiro().orElse(null);
            case SEQUENZA -> {
                GeneratedThrow tiro = SEQUENZA_FISSA.get(indiceSequenza % SEQUENZA_FISSA.size());
                indiceSequenza++;
                yield tiro;
            }
        };
    }

    /**
     * Pubblica lo stato del sensore. 
     */
    private synchronized void pubblicaStato() {
        batteria = batteria <= 0 ? 100 : batteria - 1;
        publisher.publishStato(publisher.isConnected(), batteria);
    }

    /**
     * Reimposta i generatori dello scenario corrente. 
     */
    private synchronized void reinizializzaGeneratori() {
        generatoreCasuale = new ThrowEventGenerator(seed);
        generatorePartita = new MatchScenarioGenerator(seed);
        indiceSequenza = 0;
    }

    /**
     * Restituisce il insieme dei dati relativi a lista.
     *
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private static List<GeneratedThrow> costruisciSequenza() {
        List<GeneratedThrow> lista = new ArrayList<>();
        for (Multiplier moltiplicatore : new Multiplier[]{Multiplier.SINGLE, Multiplier.DOUBLE, Multiplier.TRIPLE}) {
            for (DartSector settore : DartSector.SETTORI_NORMALI) {
                lista.add(new GeneratedThrow(settore, moltiplicatore));
            }
        }
        lista.add(new GeneratedThrow(DartSector.BULL, Multiplier.OUTER_BULL));
        lista.add(new GeneratedThrow(DartSector.BULL, Multiplier.INNER_BULL));
        return lista;
    }
}
