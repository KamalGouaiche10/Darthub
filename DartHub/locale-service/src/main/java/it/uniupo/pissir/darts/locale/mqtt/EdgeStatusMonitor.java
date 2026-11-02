package it.uniupo.pissir.darts.locale.mqtt;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task periodico: marca OFFLINE i dispositivi edge il cui ultimo_heartbeat è più vecchio della loro soglia_offline_s, e porta i relativi bersagli a OFFLINE. 
 */
public final class EdgeStatusMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(EdgeStatusMonitor.class);

    private final ConnectionProvider connectionProvider;
    private final EdgeDeviceDao edgeDeviceDao;
    private final DartboardDao dartboardDao;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "edge-status-monitor");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Inizializza {@code EdgeStatusMonitor} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param edgeDeviceDao componente {@link it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao} utilizzato dall'operazione
     * @param dartboardDao componente {@link it.uniupo.pissir.darts.locale.dao.DartboardDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public EdgeStatusMonitor(ConnectionProvider connectionProvider, EdgeDeviceDao edgeDeviceDao, DartboardDao dartboardDao) {
        this.connectionProvider = connectionProvider;
        this.edgeDeviceDao = edgeDeviceDao;
        this.dartboardDao = dartboardDao;
    }

    /**
     * Avvia l'elaborazione del componente.
     *
     * @param intervalSeconds intervallo in secondi
     *  
     */
    public void start(int intervalSeconds) {
        executor.scheduleAtFixedRate(this::checkOnce, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        LOG.info("EdgeStatusMonitor avviato, controllo ogni {}s", intervalSeconds);
    }

    /**
     * Ferma l'attività del componente. 
     */
    public void stop() {
        executor.shutdownNow();
    }

    /**
     * Esegue un ciclo del controllo degli edge. 
     */
    void checkOnce() {
        try {
            Instant now = Instant.now();
            for (EdgeDevice device : edgeDeviceDao.findOnline()) {
                if (device.ultimoHeartbeat() == null) {
                    continue;
                }
                Instant cutoff = device.ultimoHeartbeat().plusSeconds(device.sogliaOfflineS());
                if (now.isAfter(cutoff)) {
                    markOffline(device);
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Controllo periodico dei dispositivi edge fallito", e);
        }
    }

    /**
     * Registra lo stato offline.
     *
     * @param device dispositivo edge da elaborare
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    private void markOffline(EdgeDevice device) {
        boolean changed = connectionProvider.withTransaction(connection -> {
            if (!edgeDeviceDao.markOfflineIfHeartbeatUnchanged(connection, device.id(), device.ultimoHeartbeat())) {
                return false;
            }
            dartboardDao.markOfflineIfAvailable(connection, device.dartboardId());
            return true;
        });
        if (changed) {
            LOG.info("Dispositivo edge {} (bersaglio {}) marcato OFFLINE per silenzio prolungato", device.id(),
                    device.dartboardId());
        }
    }
}
