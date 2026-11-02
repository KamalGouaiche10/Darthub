package it.uniupo.pissir.darts.locale.mqtt;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.locale.model.EdgeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pubblica su darts/{localeId/{dartboardId}/config} con QoS 1 e RETAINED=true, così un edge appena avviato riceve subito la configurazione corrente. 
 */
public final class EdgeConfigPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EdgeConfigPublisher.class);
    private static final int QOS_RETAINED_CONFIG = 1;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code EdgeConfigPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public EdgeConfigPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param config configurazione del componente
     * @see it.uniupo.pissir.darts.locale.model.EdgeConfig
     *  
     */
    public void publish(long localeId, long dartboardId, EdgeConfig config) {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: configurazione non pubblicata per locale {} bersaglio {}",
                    localeId, dartboardId);
            return;
        }
        eventBus.publish(MqttTopics.giochiConfig(localeId, dartboardId), config, QOS_RETAINED_CONFIG, true);
    }
}
