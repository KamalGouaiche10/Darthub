package it.uniupo.pissir.darts.tournament.mqtt;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.tournament.service.StandingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sottoscrive eventi/partite/completata (QoS 2, 5) e passa ogni evento a StandingsService. 
 */
public final class MatchCompletedSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(MatchCompletedSubscriber.class);
    private static final int QOS = 2;

    private final MqttEventBus eventBus;
    private final StandingsService standingsService;

    /**
     * Inizializza {@code MatchCompletedSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param standingsService componente {@link it.uniupo.pissir.darts.tournament.service.StandingsService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public MatchCompletedSubscriber(MqttEventBus eventBus, StandingsService standingsService) {
        this.eventBus = eventBus;
        this.standingsService = standingsService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: MatchCompletedSubscriber non sottoscritto");
            return;
        }
        eventBus.subscribe(MqttTopics.EVENTI_PARTITE_COMPLETATA + "/#", QOS, MatchCompletedEventDTO.class,
                (topic, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", MqttTopics.EVENTI_PARTITE_COMPLETATA);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    private void gestisci(MatchCompletedEventDTO evento) {
        try {
            standingsService.gestisciCompletamento(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
