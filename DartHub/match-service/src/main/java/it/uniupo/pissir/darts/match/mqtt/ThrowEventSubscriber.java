package it.uniupo.pissir.darts.match.mqtt;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.match.service.ThrowIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sottoscrive giochi/+/+/evento (QoS 2) e passa ogni evento a ThrowIngestionService, qualunque sia il tipo di gioco. 
 */
public final class ThrowEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(ThrowEventSubscriber.class);
    private static final int QOS_EVENTO = 2;

    private final MqttEventBus eventBus;
    private final ThrowIngestionService throwIngestionService;

    /**
     * Inizializza {@code ThrowEventSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param throwIngestionService componente {@link it.uniupo.pissir.darts.match.service.ThrowIngestionService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public ThrowEventSubscriber(MqttEventBus eventBus, ThrowIngestionService throwIngestionService) {
        this.eventBus = eventBus;
        this.throwIngestionService = throwIngestionService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: ThrowEventSubscriber non sottoscritto (giochi/+/+/evento)");
            return;
        }
        eventBus.subscribe(MqttTopics.GIOCHI_EVENTO_WILDCARD, QOS_EVENTO, GameEvent.class, this::gestisci);
        LOG.info("Sottoscritto a {}", MqttTopics.GIOCHI_EVENTO_WILDCARD);
    }

    /**
     * Elabora l'evento ricevuto dalla sottoscrizione MQTT.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    private void gestisci(String topic, GameEvent evento) {
        try {
            boolean accettato = throwIngestionService.ingerisci(evento);
            if (!accettato) {
                LOG.info("Evento scartato dall'ingestion per il topic {}: {}", topic, evento);
            }
        } catch (RuntimeException e) {
            LOG.error("Gestione dell'evento fallita per il topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
