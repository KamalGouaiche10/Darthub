package it.uniupo.pissir.darts.statistics.mqtt;

import it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sub eventi/utenti/conteggio-cambiato (QoS 1, 6). 
 */
public final class UserCountSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(UserCountSubscriber.class);
    private static final int QOS = 1;

    private final MqttEventBus eventBus;
    private final StatisticsAggregationService statisticsAggregationService;

    /**
     * Inizializza {@code UserCountSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param statisticsAggregationService componente {@link it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public UserCountSubscriber(MqttEventBus eventBus, StatisticsAggregationService statisticsAggregationService) {
        this.eventBus = eventBus;
        this.statisticsAggregationService = statisticsAggregationService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: UserCountSubscriber non sottoscritto");
            return;
        }
        eventBus.subscribe(MqttTopics.EVENTI_UTENTI_CONTEGGIO, QOS, UserCountChangedEventDTO.class,
                (topic, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", MqttTopics.EVENTI_UTENTI_CONTEGGIO);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO
     *  
     */
    private void gestisci(UserCountChangedEventDTO evento) {
        try {
            statisticsAggregationService.gestisciContoUtente(evento);
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento {} fallita: {}", evento.eventId(), e.getMessage(), e);
        }
    }
}
