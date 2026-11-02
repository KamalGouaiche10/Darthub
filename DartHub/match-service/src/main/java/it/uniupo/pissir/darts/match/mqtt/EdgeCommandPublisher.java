package it.uniupo.pissir.darts.match.mqtt;

import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttOperationException;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.match.dto.EdgeCommandDTO;
import it.uniupo.pissir.darts.match.dto.ScorePushDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pubblica giochi/{localeId/{dartboardId}/comandi} (QoS 2: START_MATCH con roster obbligatorio, END_MATCH) e.../punteggio (QoS 0, per il monitor dell'edge) —. 
 */
public final class EdgeCommandPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EdgeCommandPublisher.class);
    private static final int QOS_COMANDI = 2;
    private static final int QOS_PUNTEGGIO = 0;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code EdgeCommandPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public EdgeCommandPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Invia il comando al dispositivo edge.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.match.dto.EdgeCommandDTO
     *  
     */
    public void publishCommand(long localeId, long dartboardId, EdgeCommandDTO comando) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.giochiComandi(localeId, dartboardId), comando, QOS_COMANDI, true);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione del comando {} fallita per locale {} bersaglio {}: {}",
                    comando.comando(), localeId, dartboardId, e.getMessage());
        }
    }

    /**
     * Invia il comando segnalando gli errori di pubblicazione.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.match.dto.EdgeCommandDTO
     *  
     */
    public void publishCommandOrThrow(long localeId, long dartboardId, EdgeCommandDTO comando) {
        if (eventBus == null) {
            LOG.warn("Broker MQTT non configurato: comando {} per locale {} bersaglio {} non pubblicato "
                    + "(servizio avviato in modalità degradata)", comando.comando(), localeId, dartboardId);
            return;
        }
        try {
            eventBus.publishOrThrow(MqttTopics.giochiComandi(localeId, dartboardId), comando, QOS_COMANDI, true);
        } catch (MqttOperationException e) {
            throw new ServiceUnavailableException(
                    "Impossibile notificare l'edge del bersaglio " + dartboardId + " (comando " + comando.comando()
                            + "): broker MQTT non raggiungibile in questo momento. Riprova tra qualche istante.");
        }
    }

    /**
     * Invia il comando richiesto per completare l'operazione.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.match.dto.EdgeCommandDTO
     *  
     */
    public void publishCommandRequired(long localeId, long dartboardId, EdgeCommandDTO comando) {
        requireAvailable();
        publishCommandOrThrow(localeId, dartboardId, comando);
    }

    /**
     * Richiede che il bersaglio sia disponibile.
     *
     * @throws ServiceUnavailableException se viene rilevato l'errore «Broker MQTT non configurato: il simulatore del bersaglio non è disponibile»
     *  
     */
    public void requireAvailable() {
        if (eventBus == null) {
            throw new ServiceUnavailableException("Broker MQTT non configurato: il simulatore del bersaglio non è disponibile.");
        }
    }

    /**
     * Pubblica il punteggio corrente della partita.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param punteggio punteggio da elaborare
     * @see it.uniupo.pissir.darts.match.dto.ScorePushDTO
     *  
     */
    public void publishScore(long localeId, long dartboardId, ScorePushDTO punteggio) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.giochiPunteggio(localeId, dartboardId), punteggio, QOS_PUNTEGGIO);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione del punteggio fallita per locale {} bersaglio {}: {}",
                    localeId, dartboardId, e.getMessage());
        }
    }
}
