package it.uniupo.pissir.darts.statistics.mqtt;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sub eventi/partite/completata (QoS 2, 6). 
 */
public final class MatchCompletedSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(MatchCompletedSubscriber.class);
    private static final int QOS = 2;

    private final MqttEventBus eventBus;
    private final StatisticsAggregationService statisticsAggregationService;

    /**
     * Inizializza {@code MatchCompletedSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param statisticsAggregationService componente {@link it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public MatchCompletedSubscriber(MqttEventBus eventBus, StatisticsAggregationService statisticsAggregationService) {
        this.eventBus = eventBus;
        this.statisticsAggregationService = statisticsAggregationService;
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
            statisticsAggregationService.gestisciPartitaCompletata(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
