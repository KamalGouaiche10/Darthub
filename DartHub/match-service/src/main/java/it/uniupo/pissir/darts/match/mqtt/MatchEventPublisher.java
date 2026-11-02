package it.uniupo.pissir.darts.match.mqtt;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pubblica gli eventi MQTT relativi a partite. 
 */
public final class MatchEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(MatchEventPublisher.class);
    private static final int QOS_EVENTO = 0;
    private static final int QOS_COMPLETATA = 2;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code MatchEventPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public MatchEventPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Pubblica l'evento relativo al tiro.
     *
     * @param event evento da elaborare
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    public void publishThrowEvent(GameEvent event) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.EVENTI_PARTITE_EVENTO, event, QOS_EVENTO);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione di eventi/partite/evento fallita: {}", e.getMessage());
        }
    }

    /**
     * Pubblica il risultato conclusivo della partita.
     *
     * @param event evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public void publishMatchCompleted(MatchCompletedEventDTO event) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.EVENTI_PARTITE_COMPLETATA, event, QOS_COMPLETATA);
            eventBus.publish(MqttTopics.EVENTI_PARTITE_COMPLETATA + "/" + event.matchId(), event, QOS_COMPLETATA, true);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione di eventi/partite/completata fallita: {}", e.getMessage());
        }
    }
}
