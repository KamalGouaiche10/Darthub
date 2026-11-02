package it.uniupo.pissir.darts.statistics.mqtt;

import it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sub eventi/tornei/aggiornato (QoS 2, 6). 
 */
public final class TournamentUpdatedSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(TournamentUpdatedSubscriber.class);
    private static final int QOS = 2;

    private final MqttEventBus eventBus;
    private final StatisticsAggregationService statisticsAggregationService;

    /**
     * Inizializza {@code TournamentUpdatedSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param statisticsAggregationService componente {@link it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public TournamentUpdatedSubscriber(MqttEventBus eventBus, StatisticsAggregationService statisticsAggregationService) {
        this.eventBus = eventBus;
        this.statisticsAggregationService = statisticsAggregationService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: TournamentUpdatedSubscriber non sottoscritto");
            return;
        }
        eventBus.subscribe(MqttTopics.EVENTI_TORNEI_AGGIORNATO, QOS, TournamentUpdatedEventDTO.class,
                (topic, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", MqttTopics.EVENTI_TORNEI_AGGIORNATO);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO
     *  
     */
    private void gestisci(TournamentUpdatedEventDTO evento) {
        try {
            statisticsAggregationService.gestisciTorneoAggiornato(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
