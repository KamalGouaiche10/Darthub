package it.uniupo.pissir.darts.tournament.mqtt;

import it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pubblica eventi/tornei/aggiornato (QoS 2, 5, §7). 
 */
public final class TournamentEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(TournamentEventPublisher.class);
    private static final int QOS = 2;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code TournamentEventPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public TournamentEventPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param event evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO
     *  
     */
    public void publish(TournamentUpdatedEventDTO event) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.EVENTI_TORNEI_AGGIORNATO, event, QOS);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione di eventi/tornei/aggiornato fallita: {}", e.getMessage());
        }
    }
}
