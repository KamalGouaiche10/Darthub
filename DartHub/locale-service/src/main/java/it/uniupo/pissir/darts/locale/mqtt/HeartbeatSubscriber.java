package it.uniupo.pissir.darts.locale.mqtt;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Sub edge/+/heartbeat, QoS 0. 
 */
public final class HeartbeatSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatSubscriber.class);

    private final MqttEventBus eventBus;
    private final ConnectionProvider connectionProvider;
    private final EdgeDeviceDao edgeDeviceDao;
    private final DartboardDao dartboardDao;

    /**
     * Inizializza {@code HeartbeatSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBus bus per la pubblicazione e la ricezione MQTT
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param edgeDeviceDao componente {@link it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao} utilizzato dall'operazione
     * @param dartboardDao componente {@link it.uniupo.pissir.darts.locale.dao.DartboardDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public HeartbeatSubscriber(MqttEventBus eventBus, ConnectionProvider connectionProvider, EdgeDeviceDao edgeDeviceDao,
                               DartboardDao dartboardDao) {
        this.eventBus = eventBus;
        this.connectionProvider = connectionProvider;
        this.edgeDeviceDao = edgeDeviceDao;
        this.dartboardDao = dartboardDao;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBus == null) {
            LOG.warn("MQTT non disponibile: HeartbeatSubscriber non attivo");
            return;
        }
        eventBus.subscribe(MqttTopics.EDGE_HEARTBEAT_WILDCARD, 0, HeartbeatPayload.class, this::handle);
        LOG.info("Sottoscritto a {}", MqttTopics.EDGE_HEARTBEAT_WILDCARD);
    }

    /**
     * Riceve il segnale di presenza e aggiorna lo stato del dispositivo.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @param payload contenuto del messaggio
     * @see it.uniupo.pissir.darts.locale.mqtt.HeartbeatPayload
     *  
     */
    void handle(String topic, HeartbeatPayload payload) {
        long localeId = extractLocaleId(topic);
        long dartboardId = extractDartboardId(topic);
        EdgeDeviceStatus stato = parseStato(payload.stato());
        Instant timestamp = parseTimestamp(payload.timestamp());
        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId,
                    timestamp, payload.tiriInCoda(), stato);
            if (stato == EdgeDeviceStatus.ONLINE) {
                dartboardDao.restoreAvailableForOnlineEdges(connection, localeId);
            }
            return null;
        });
        LOG.debug("Heartbeat locale {} bersaglio {}: stato={}, tiriInCoda={}",
                localeId, dartboardId, stato, payload.tiriInCoda());
    }

    /**
     * Estrae l'identificativo del locale dal topic o dai dati ricevuti.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @return il valore numerico calcolato o letto
     *  
     */
    private long extractLocaleId(String topic) {

        String[] segments = topic.split("/");
        return Long.parseLong(segments[1]);
    }

    /**
     * Estrae l'identificativo del bersaglio dal topic o dai dati ricevuti.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @return il valore numerico calcolato o letto
     *  
     */
    private long extractDartboardId(String topic) {
        return Long.parseLong(topic.split("/")[2]);
    }

    /**
     * Legge e converte stato dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus
     *  
     */
    private EdgeDeviceStatus parseStato(String value) {
        try {
            return EdgeDeviceStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return EdgeDeviceStatus.ONLINE;
        }
    }

    /**
     * Legge e converte timestamp dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
     * @see java.time.Instant
     *  
     */
    private Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
