package it.uniupo.pissir.darts.locale.mqtt;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.DartboardStatus;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifica il comportamento di {@code EdgeConnectivity} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class EdgeConnectivityTest {

    @TempDir Path tempDir;

    private ConnectionProvider connectionProvider;
    private EdgeDeviceDao edgeDeviceDao;
    private DartboardDao dartboardDao;
    private EdgeStatusMonitor monitor;
    private HeartbeatSubscriber subscriber;
    private long localeId;
    private long dartboardId;
    private long edgeId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        edgeDeviceDao = new EdgeDeviceDao(connectionProvider);
        dartboardDao = new DartboardDao(connectionProvider);
        localeId = new LocaleDao(connectionProvider).insert(new Locale(0, "Bar", "Via Roma",
                LocaleType.PUBBLICO, 3L, LocaleStatus.APPROVATO, Instant.now())).id();
        dartboardId = dartboardDao.insert(new Dartboard(0, localeId, "B1", 5L,
                DartboardStatus.DISPONIBILE, Instant.now())).id();
        edgeId = edgeDeviceDao.insert(new EdgeDevice(0, dartboardId, localeId, "SN-1", 30, 90,
                2, null, null, 0, EdgeDeviceStatus.OFFLINE, null)).id();
        monitor = new EdgeStatusMonitor(connectionProvider, edgeDeviceDao, dartboardDao);
        subscriber = new HeartbeatSubscriber(null, connectionProvider, edgeDeviceDao, dartboardDao);
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        monitor.stop();
        connectionProvider.close();
    }

    /**
     * Verifica il caso segnale di presenza di un bersaglio non rende online gli altri edge del locale. 
     */
    @Test
    void heartbeatDiUnBersaglioNonRendeOnlineGliAltriEdgeDelLocale() {
        long secondoBersaglio = dartboardDao.insert(new Dartboard(0, localeId, "B2", 5L,
                DartboardStatus.DISPONIBILE, Instant.now())).id();
        long secondoEdge = edgeDeviceDao.insert(new EdgeDevice(0, secondoBersaglio, localeId, "SN-2", 30, 90,
                2, null, null, 0, EdgeDeviceStatus.OFFLINE, null)).id();

        subscriber.handle("edge/" + localeId + "/" + dartboardId + "/heartbeat", new HeartbeatPayload(
                Instant.now().toString(), "ONLINE", 0));

        assertEquals(EdgeDeviceStatus.ONLINE, edgeDeviceDao.findById(edgeId).orElseThrow().stato());
        assertEquals(EdgeDeviceStatus.OFFLINE, edgeDeviceDao.findById(secondoEdge).orElseThrow().stato());
    }

    /**
     * Verifica il caso bersaglio libero torna disponibile al ritorno dell edge. 
     */
    @Test
    void bersaglioLiberoTornaDisponibileAlRitornoDellEdge() {
        Instant vecchio = Instant.now().minusSeconds(120);
        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, vecchio, 0, EdgeDeviceStatus.ONLINE);
            return null;
        });

        monitor.checkOnce();
        assertEquals(EdgeDeviceStatus.OFFLINE, edgeDeviceDao.findById(edgeId).orElseThrow().stato());
        assertEquals(DartboardStatus.OFFLINE, dartboardDao.findById(dartboardId).orElseThrow().stato());

        subscriber.handle("edge/" + localeId + "/" + dartboardId + "/heartbeat", new HeartbeatPayload(
                Instant.now().toString(), "ONLINE", 0));

        assertEquals(EdgeDeviceStatus.ONLINE, edgeDeviceDao.findById(edgeId).orElseThrow().stato());
        assertEquals(DartboardStatus.DISPONIBILE, dartboardDao.findById(dartboardId).orElseThrow().stato());
    }

    /**
     * Verifica il caso perdita dell edge non cancella partita o mantenimento. 
     */
    @Test
    void perditaDellEdgeNonCancellaPartitaOMantenimento() {
        for (DartboardStatus stato : new DartboardStatus[]{DartboardStatus.IN_USO, DartboardStatus.MANUTENZIONE}) {
            Dartboard current = dartboardDao.findById(dartboardId).orElseThrow();
            dartboardDao.update(new Dartboard(current.id(), current.localeId(), current.identificativoLocale(),
                    current.gameTypeId(), stato, current.createdAt()));
            connectionProvider.withTransaction(connection -> {
                edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, Instant.now().minusSeconds(120),
                        0, EdgeDeviceStatus.ONLINE);
                return null;
            });

            monitor.checkOnce();
            subscriber.handle("edge/" + localeId + "/" + dartboardId + "/heartbeat", new HeartbeatPayload(
                    Instant.now().toString(), "ONLINE", 0));

            assertEquals(stato, dartboardDao.findById(dartboardId).orElseThrow().stato());
        }
    }

    /**
     * Verifica il caso snapshot vecchio non puo sovrascrivere un segnale di presenza nuovo. 
     */
    @Test
    void snapshotVecchioNonPuoSovrascrivereUnHeartbeatNuovo() {
        Instant vecchio = Instant.now().minusSeconds(120);
        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, vecchio, 0, EdgeDeviceStatus.ONLINE);
            return null;
        });
        subscriber.handle("edge/" + localeId + "/" + dartboardId + "/heartbeat", new HeartbeatPayload(
                Instant.now().toString(), "ONLINE", 0));

        boolean changed = connectionProvider.withTransaction(connection ->
                edgeDeviceDao.markOfflineIfHeartbeatUnchanged(connection, edgeId, vecchio));

        assertFalse(changed);
        assertEquals(EdgeDeviceStatus.ONLINE, edgeDeviceDao.findById(edgeId).orElseThrow().stato());
    }

    /**
     * Verifica il caso offline manuale resta offline dopo un segnale di presenza. 
     */
    @Test
    void offlineManualeRestaOfflineDopoUnHeartbeat() {
        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, Instant.now().minusSeconds(120),
                    0, EdgeDeviceStatus.ONLINE);
            return null;
        });
        monitor.checkOnce();
        Dartboard current = dartboardDao.findById(dartboardId).orElseThrow();
        dartboardDao.update(new Dartboard(current.id(), current.localeId(), current.identificativoLocale(),
                current.gameTypeId(), DartboardStatus.OFFLINE, current.createdAt()));

        subscriber.handle("edge/" + localeId + "/" + dartboardId + "/heartbeat", new HeartbeatPayload(
                Instant.now().toString(), "ONLINE", 0));

        assertEquals(DartboardStatus.OFFLINE, dartboardDao.findById(dartboardId).orElseThrow().stato());
    }
}
