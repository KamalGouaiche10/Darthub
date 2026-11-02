package it.uniupo.pissir.darts.simulator.cli;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.simulator.generator.DartSector;
import it.uniupo.pissir.darts.simulator.generator.GeneratedThrow;
import it.uniupo.pissir.darts.simulator.generator.Multiplier;
import it.uniupo.pissir.darts.simulator.generator.Scenario;
import it.uniupo.pissir.darts.simulator.service.SimulatorEngine;
import it.uniupo.pissir.darts.simulator.service.SimulatorStato;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Interpreta i comandi interattivi del sensore simulato. 
 */
public final class SimulatorConsole {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorConsole.class);

    private final SimulatorEngine engine;

    /**
     * Inizializza {@code SimulatorConsole} con i dati e le dipendenze indicati.
     *
     * @param engine engine da utilizzare
     * @see it.uniupo.pissir.darts.simulator.service.SimulatorEngine
     *  
     */
    public SimulatorConsole(SimulatorEngine engine) {
        this.engine = engine;
        engine.setOnTiro(tiro -> System.out.println(
                "Pubblicato: " + tiro.settore() + " " + tiro.moltiplicatore() + " (" + tiro.punti() + " punti)"));
        engine.setOnScenarioConcluso(() -> System.out.println(
                "Scenario PARTITA_501 concluso. Usa 'scenario partita_501' per iniziarne una nuova."));
        engine.setOnRiconnesso(secondi -> System.out.println("Riconnesso dopo " + secondi + "s"));
    }

    /**
     * Avvia loop comandi per il simulator console. 
     */
    public void avviaLoopComandi() {
        stampaAiuto();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (!gestisciComando(linea.trim())) {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error("Errore di lettura da stdin", e);
        }
        engine.chiudi();
    }

    /**
     * Mostra i comandi disponibili nella console del simulatore. 
     */
    private void stampaAiuto() {
        SimulatorStato stato = engine.statoCorrente();
        System.out.println("sensor-simulator pronto (scenario=" + stato.scenario() + ", intervallo=" + stato.intervalloMs() + "ms).");
        System.out.println("Comandi: start | stop | velocita <ms> | scenario <RANDOM|PARTITA_501|SEQUENZA> | "
                + "tiro <settore> <moltiplicatore> | disconnect <secondi> | quit");
    }

    /**
     * Gestisce comando per il simulator console.
     *
     * @param linea linea da utilizzare
     * @return l'esito della condizione verificata
     *  
     */
    private boolean gestisciComando(String linea) {
        if (linea.isEmpty()) {
            return true;
        }
        String[] parti = linea.split("\\s+");
        switch (parti[0].toLowerCase(Locale.ROOT)) {
            case "start" -> start();
            case "stop" -> stop();
            case "velocita" -> cambiaVelocita(parti);
            case "scenario" -> cambiaScenario(parti);
            case "tiro" -> tiroManuale(parti);
            case "disconnect" -> disconnetti(parti);
            case "quit" -> {
                return false;
            }
            default -> System.out.println("Comando sconosciuto: " + parti[0]);
        }
        return true;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    private void start() {
        if (engine.avvia()) {
            SimulatorStato stato = engine.statoCorrente();
            System.out.println("Avviato (scenario=" + stato.scenario() + ", intervallo=" + stato.intervalloMs() + "ms)");
        } else {
            System.out.println("Già in esecuzione.");
        }
    }

    /**
     * Ferma l'attività del componente. 
     */
    private void stop() {
        if (engine.ferma()) {
            System.out.println("Fermato.");
        }
    }

    /**
     * Cambia velocita per il simulator console.
     *
     * @param parti parti da utilizzare
     *  
     */
    private void cambiaVelocita(String[] parti) {
        if (parti.length < 2) {
            System.out.println("Uso: velocita <ms>");
            return;
        }
        try {
            long nuovoIntervalloMs = Long.parseLong(parti[1]);
            engine.cambiaVelocita(nuovoIntervalloMs);
            System.out.println("Intervallo impostato a " + nuovoIntervalloMs + "ms");
        } catch (NumberFormatException e) {
            System.out.println("Valore non valido: " + parti[1]);
        } catch (ValidationException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Cambia scenario per il simulator console.
     *
     * @param parti parti da utilizzare
     *  
     */
    private void cambiaScenario(String[] parti) {
        if (parti.length < 2) {
            System.out.println("Uso: scenario <RANDOM|PARTITA_501|SEQUENZA>");
            return;
        }
        try {
            Scenario nuovo = Scenario.valueOf(parti[1].toUpperCase(Locale.ROOT));
            engine.cambiaScenario(nuovo);
            System.out.println("Scenario impostato a " + nuovo);
        } catch (IllegalArgumentException e) {
            System.out.println("Scenario non valido: " + parti[1]);
        }
    }

    /**
     * Invia un tiro scelto esplicitamente dall'utente.
     *
     * @param parti parti da utilizzare
     *  
     */
    private void tiroManuale(String[] parti) {
        if (parti.length < 3) {
            System.out.println("Uso: tiro <settore> <moltiplicatore>");
            return;
        }
        try {
            DartSector settore = DartSector.valueOf(parti[1].toUpperCase(Locale.ROOT));
            Multiplier moltiplicatore = Multiplier.valueOf(parti[2].toUpperCase(Locale.ROOT));
            GeneratedThrow tiro = engine.tiroManuale(settore, moltiplicatore);
            System.out.println("Tiro manuale pubblicato: " + settore + " " + moltiplicatore + " (" + tiro.punti() + " punti)");
        } catch (IllegalArgumentException e) {
            System.out.println("Settore o moltiplicatore non valido");
        }
    }

    /**
     * Interrompe la connessione MQTT del sensore.
     *
     * @param parti parti da utilizzare
     *  
     */
    private void disconnetti(String[] parti) {
        if (parti.length < 2) {
            System.out.println("Uso: disconnect <secondi>");
            return;
        }
        long secondi;
        try {
            secondi = Long.parseLong(parti[1]);
        } catch (NumberFormatException e) {
            System.out.println("Valore non valido: " + parti[1]);
            return;
        }
        try {
            engine.disconnetti(secondi);
            System.out.println("Disconnesso per " + secondi + "s (i tiri pubblicati in questa finestra falliranno, "
                    + "come un guasto reale del sensore)");
        } catch (MqttException e) {
            System.out.println("Disconnessione fallita: " + e.getMessage());
        }
    }
}
