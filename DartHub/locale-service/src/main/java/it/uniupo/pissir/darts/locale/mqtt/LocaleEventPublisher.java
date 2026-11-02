package it.uniupo.pissir.darts.locale.mqtt;

import it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Pubblica eventi/locali/conteggio-cambiato (QoS 1) verso statistics-service, l'unico consumatore. 
 */
public final class LocaleEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LocaleEventPublisher.class);
    private static final int QOS = 1;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code LocaleEventPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public LocaleEventPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Pubblica il nuovo contributo della risorsa alle statistiche.
     *
     * @param localeId identificativo del locale
     * @param conta indicatore di inclusione nel conteggio statistico
     *  
     */
    public void publishConteggioCambiato(long localeId, boolean conta) {
        publish(UUID.randomUUID().toString(), localeId, conta);
    }

    /**
     * Pubblica backfill tramite il collegamento MQTT.
     *
     * @param localeId identificativo del locale
     *  
     */
    public void publishBackfill(long localeId) {
        publish("backfill-locale-" + localeId, localeId, true);
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param eventId identificativo dell'evento
     * @param localeId identificativo del locale
     * @param conta indicatore di inclusione nel conteggio statistico
     *  
     */
    private void publish(String eventId, long localeId, boolean conta) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.EVENTI_LOCALI_CONTEGGIO,
                    new LocaleCountChangedEventDTO(eventId, localeId, conta), QOS);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione di eventi/locali/conteggio-cambiato fallita: {}", e.getMessage());
        }
    }
}
