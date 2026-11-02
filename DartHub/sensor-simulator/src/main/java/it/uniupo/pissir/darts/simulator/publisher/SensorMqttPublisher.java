package it.uniupo.pissir.darts.simulator.publisher;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.simulator.generator.GeneratedThrow;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Pubblica gli eventi MQTT relativi a sensore MQTT. 
 */
public final class SensorMqttPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(SensorMqttPublisher.class);
    private static final int QOS_TIRO = 2;
    private static final int QOS_STATO = 0;

    private final MqttClient client;
    private final MqttConnectOptions connectOptions;
    private final MqttEventBus eventBus;
    private final long localeId;
    private final long dartboardId;
    private volatile Long activeMatchId;
    private volatile Runnable onMatchChanged = () -> { };

    /**
     * Raccoglie i dati di partita.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param comando comando da applicare al dispositivo
     * @param matchId identificativo della partita
     *  
     */
    private record MatchCommand(String comando, long matchId) { }

    /**
     * Inizializza {@code SensorMqttPublisher} con i dati e le dipendenze indicati.
     *
     * @param client client da utilizzare
     * @param connectOptions opzioni della connessione MQTT
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    public SensorMqttPublisher(MqttClient client, MqttConnectOptions connectOptions, long localeId, long dartboardId) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.eventBus = new MqttEventBus(client);
        this.localeId = localeId;
        this.dartboardId = dartboardId;
        eventBus.subscribe(MqttTopics.giochiComandi(localeId, dartboardId), 2, MatchCommand.class, (topic, command) -> {
            Long next = activeMatchId;
            if ("START_MATCH".equals(command.comando())) next = command.matchId();
            else if ("END_MATCH".equals(command.comando())
                    && java.util.Objects.equals(activeMatchId, command.matchId())) next = null;
            if (!java.util.Objects.equals(activeMatchId, next)) {
                activeMatchId = next;
                onMatchChanged.run();
            }
        });
    }

    /**
     * Pubblica il tiro sul collegamento MQTT.
     *
     * @param tiro tiro da utilizzare
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    public boolean publishThrow(GeneratedThrow tiro) {
        try {
            ThrowSensorEvent event = new ThrowSensorEvent(tiro.settore().name(), tiro.moltiplicatore().name(),
                    Instant.now().toString());
            eventBus.publish(MqttTopics.sensoreEvento(localeId, dartboardId), event, QOS_TIRO);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione del tiro fallita (probabilmente disconnesso): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pubblica lo stato del sensore.
     *
     * @param online indicatore di connessione del dispositivo
     * @param batteria livello della batteria del sensore
     *  
     */
    public void publishStato(boolean online, int batteria) {
        try {
            eventBus.publish(MqttTopics.sensoreStato(localeId, dartboardId), new StatoSensorEvent(online, batteria), QOS_STATO);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione dello stato fallita: {}", e.getMessage());
        }
    }

    /**
     * Indica se il collegamento MQTT è attivo.
     *
     * @return {@code true} se il collegamento MQTT è attivo; {@code false} altrimenti
     *  
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Restituisce l'identificativo del locale configurato.
     *
     * @return identificativo del locale
     *  
     */
    public long localeId() {
        return localeId;
    }

    /**
     * Restituisce l'identificativo del bersaglio configurato.
     *
     * @return identificativo del bersaglio
     *  
     */
    public long dartboardId() {
        return dartboardId;
    }

    /**
     * Restituisce l'identificativo del attivo partita.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    public Long activeMatchId() {
        return activeMatchId;
    }

    /**
     * Imposta su partita changed per il componente.
     *
     * @param listener ascoltatore da registrare
     *  
     */
    public void setOnMatchChanged(Runnable listener) {
        onMatchChanged = listener;
    }

    /**
     * Interrompe la connessione MQTT del sensore.
     *
     * @throws MqttException se l'operazione non può essere completata
     *  
     */
    public void disconnect() throws MqttException {
        if (client.isConnected()) {
            client.disconnect();
        }
    }

    /**
     * Ripristina la connessione MQTT del sensore.
     *
     * @throws MqttException se l'operazione non può essere completata
     *  
     */
    public void reconnect() throws MqttException {
        if (!client.isConnected()) {
            client.connect(connectOptions);
        }
    }
}
