package it.uniupo.pissir.darts.edge.mqtt;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pubblica su giochi/{localeId/{dartboardId}/evento} (broker CENTRALE, QoS 2) — il canale su cui match-service riceve i tiri live. 
 */
public final class CloudPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(CloudPublisher.class);
    private volatile int qosTiro = 2;

    private final MqttEventBus eventBus;
    private final long localeId;
    private final long dartboardId;

    /**
     * Inizializza {@code CloudPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public CloudPublisher(MqttEventBus eventBus, long localeId, long dartboardId) {
        this.eventBus = eventBus;
        this.localeId = localeId;
        this.dartboardId = dartboardId;
    }

    /**
     * Pubblica il tiro sul collegamento MQTT.
     *
     * @param matchId identificativo della partita
     * @param serverParticipantId identificativo del partecipante sul server
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @param timestampSensore istante registrato dal sensore
     * @see it.uniupo.pissir.darts.game.model.darts.DartSector
     *  
     */
    public void pubblicaTiro(long matchId, long serverParticipantId, DartSector settore, Multiplier moltiplicatore,
                              int punti, Instant timestampSensore) {
        if (eventBus == null) {
            LOG.warn("MQTT centrale non disponibile: tiro non pubblicato dal vivo (resterà in coda per il sync)");
            return;
        }

        GameEvent evento = new GameEvent(UUID.randomUUID().toString(), matchId, serverParticipantId, null, null, "TIRO",
                Map.of("settore", settore.name(), "moltiplicatore", moltiplicatore.name()), punti, timestampSensore,
                "LIVE", null);
        try {
            eventBus.publish(MqttTopics.giochiEvento(localeId, dartboardId), evento, qosTiro);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione live del tiro fallita, resterà in coda per il sync: {}", e.getMessage());
        }
    }

    /**
     * Aggiorna qos per il cloud.
     *
     * @param qos livello MQTT di qualità del servizio
     * @throws IllegalArgumentException se viene rilevato l'errore «QoS non valido»
     *  
     */
    public void aggiornaQos(int qos) {
        if (qos < 0 || qos > 2) throw new IllegalArgumentException("QoS non valido");
        qosTiro = qos;
    }
}
