package it.uniupo.pissir.darts.statistics.mqtt;

import it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sub eventi/locali/conteggio-cambiato (QoS 1, 6). 
 */
public final class LocaleCountSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(LocaleCountSubscriber.class);
    private static final int QOS = 1;

    private final MqttEventBus eventBus;
    private final StatisticsAggregationService statisticsAggregationService;

    /**
     * Inizializza {@code LocaleCountSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param statisticsAggregationService componente {@link it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public LocaleCountSubscriber(MqttEventBus eventBus, StatisticsAggregationService statisticsAggregationService) {
        this.eventBus = eventBus;
        this.statisticsAggregationService = statisticsAggregationService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: LocaleCountSubscriber non sottoscritto");
            return;
        }
        eventBus.subscribe(MqttTopics.EVENTI_LOCALI_CONTEGGIO, QOS, LocaleCountChangedEventDTO.class,
                (topic, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", MqttTopics.EVENTI_LOCALI_CONTEGGIO);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO
     *  
     */
    private void gestisci(LocaleCountChangedEventDTO evento) {
        try {
            statisticsAggregationService.gestisciContoLocale(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
