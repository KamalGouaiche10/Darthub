package it.uniupo.pissir.darts.gateway.monitoring;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifica la disponibilità del broker attraverso messaggi MQTT. 
 */
public final class BrokerProbe {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerProbe.class);
    private static final long TIMEOUT_MS = 1000;

    private final MqttEventBus eventBus;
    private final AtomicReference<String> ultimoIdRicevuto = new AtomicReference<>();

    /**
     * Raccoglie i dati di ping.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del ping
     *  
     */
    public record Ping(String id) {
    }

    /**
     * Inizializza {@code BrokerProbe} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public BrokerProbe(MqttEventBus eventBus) {
        this.eventBus = eventBus;
        if (eventBus != null) {
            eventBus.subscribe(MqttTopics.SISTEMA_PING, 0, Ping.class, (topic, ping) -> ultimoIdRicevuto.set(ping.id()));
        }
    }

    /**
     * Verifica le credenziali e i dati necessari all'operazione.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public boolean verifica() {
        if (eventBus == null) {
            return false;
        }
        String id = UUID.randomUUID().toString();
        try {
            eventBus.publish(MqttTopics.SISTEMA_PING, new Ping(id), 0);
        } catch (RuntimeException e) {
            LOG.debug("Pubblicazione del ping fallita: {}", e.getMessage());
            return false;
        }
        long scadenza = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < scadenza) {
            if (id.equals(ultimoIdRicevuto.get())) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
