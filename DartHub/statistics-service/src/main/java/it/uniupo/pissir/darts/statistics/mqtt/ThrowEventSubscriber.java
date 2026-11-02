package it.uniupo.pissir.darts.statistics.mqtt;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Riceve ed elabora i messaggi MQTT relativi a tiri. 
 */
public final class ThrowEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(ThrowEventSubscriber.class);
    private static final int QOS = 0;

    private final MqttEventBus eventBus;
    private final StatisticsAggregationService statisticsAggregationService;

    /**
     * Inizializza {@code ThrowEventSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param statisticsAggregationService componente {@link it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public ThrowEventSubscriber(MqttEventBus eventBus, StatisticsAggregationService statisticsAggregationService) {
        this.eventBus = eventBus;
        this.statisticsAggregationService = statisticsAggregationService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: ThrowEventSubscriber non sottoscritto");
            return;
        }
        eventBus.subscribe(MqttTopics.EVENTI_PARTITE_EVENTO, QOS, GameEvent.class, (topic, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", MqttTopics.EVENTI_PARTITE_EVENTO);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    private void gestisci(GameEvent evento) {
        try {
            statisticsAggregationService.gestisciTiro(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
