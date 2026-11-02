package it.uniupo.pissir.darts.user.mqtt;

import it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Pubblica eventi/utenti/conteggio-cambiato (QoS 1) verso statistics-service, l'unico consumatore. 
 */
public final class UserEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final int QOS = 1;

    private final MqttEventBus eventBus;

    /**
     * Inizializza {@code UserEventPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public UserEventPublisher(MqttEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Pubblica il nuovo contributo della risorsa alle statistiche.
     *
     * @param userId identificativo dell'utente
     * @param conta indicatore di inclusione nel conteggio statistico
     *  
     */
    public void publishConteggioCambiato(long userId, boolean conta) {
        publish(UUID.randomUUID().toString(), userId, conta);
    }

    /**
     * Pubblica backfill tramite il collegamento MQTT.
     *
     * @param userId identificativo dell'utente
     *  
     */
    public void publishBackfill(long userId) {
        publish("backfill-utente-" + userId, userId, true);
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param eventId identificativo dell'evento
     * @param userId identificativo dell'utente
     * @param conta indicatore di inclusione nel conteggio statistico
     *  
     */
    private void publish(String eventId, long userId, boolean conta) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(MqttTopics.EVENTI_UTENTI_CONTEGGIO,
                    new UserCountChangedEventDTO(eventId, userId, conta), QOS);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione di eventi/utenti/conteggio-cambiato fallita: {}", e.getMessage());
        }
    }
}
