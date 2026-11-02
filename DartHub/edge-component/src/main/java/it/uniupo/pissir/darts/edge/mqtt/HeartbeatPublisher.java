package it.uniupo.pissir.darts.edge.mqtt;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.edge.model.ConnectionState;
import it.uniupo.pissir.darts.edge.service.OfflineStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pubblica periodicamente edge/{localeId/heartbeat} sul broker CENTRALE (QoS 0, 2, §7): {timestamp, stato, tiriInCoda}. 
 */
public final class HeartbeatPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatPublisher.class);

    /**
     * Stessa forma di HeartbeatPayload lato locale-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param timestamp istante dell'evento
     * @param stato stato da impostare o verificare
     * @param tiriInCoda numero di tiri in attesa di sincronizzazione
     *  
     */
    public record HeartbeatPayload(String timestamp, String stato, int tiriInCoda) {
    }

    private final MqttEventBus eventBusCentrale;
    private final long localeId;
    private final long dartboardId;
    private final OfflineStorageService offlineStorageService;
    private int intervalloSecondi;
    private java.util.concurrent.ScheduledFuture<?> task;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat-publisher");
        t.setDaemon(true);
        return t;
    });

    /**
     * Inizializza {@code HeartbeatPublisher} con i dati e le dipendenze indicati.
     *
     * @param eventBusCentrale bus MQTT del broker centrale
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param offlineStorageService componente {@link it.uniupo.pissir.darts.edge.service.OfflineStorageService} utilizzato dall'operazione
     * @param intervalloSecondi intervallo in secondi
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public HeartbeatPublisher(MqttEventBus eventBusCentrale, long localeId, long dartboardId,
                               OfflineStorageService offlineStorageService,
                               int intervalloSecondi) {
        this.eventBusCentrale = eventBusCentrale;
        this.localeId = localeId;
        this.dartboardId = dartboardId;
        this.offlineStorageService = offlineStorageService;
        this.intervalloSecondi = intervalloSecondi;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public synchronized void start() {
        if (eventBusCentrale == null) {
            LOG.warn("MQTT centrale non disponibile: HeartbeatPublisher non avviato");
            return;
        }
        if (task != null) task.cancel(false);
        task = executor.scheduleAtFixedRate(this::pubblica, 0, intervalloSecondi, TimeUnit.SECONDS);
    }

    /**
     * Aggiorna intervallo per il segnale di presenza.
     *
     * @param secondi intervallo in secondi
     * @throws IllegalArgumentException se viene rilevato l'errore «Heartbeat non valido»
     *  
     */
    public synchronized void aggiornaIntervallo(int secondi) {
        if (secondi < 1) throw new IllegalArgumentException("Heartbeat non valido");
        if (secondi == intervalloSecondi) return;
        intervalloSecondi = secondi;
        start();
    }

    /**
     * Pubblica  tramite il collegamento MQTT. 
     */
    private void pubblica() {
        try {
            int tiriInCoda;
            try {
                tiriInCoda = offlineStorageService.tiriInCoda();
            } catch (RuntimeException e) {
                LOG.warn("Conteggio tiri in coda non disponibile per locale {} bersaglio {}: {}",
                        localeId, dartboardId, e.getMessage());
                tiriInCoda = 0;
            }
            HeartbeatPayload payload = new HeartbeatPayload(Instant.now().toString(), ConnectionState.ONLINE.name(),
                    tiriInCoda);
            eventBusCentrale.publish(MqttTopics.edgeHeartbeat(localeId, dartboardId), payload, 0);
        } catch (RuntimeException e) {
            LOG.warn("Pubblicazione heartbeat fallita per locale {} bersaglio {}: {}",
                    localeId, dartboardId, e.getMessage(), e);
        }
    }
}
