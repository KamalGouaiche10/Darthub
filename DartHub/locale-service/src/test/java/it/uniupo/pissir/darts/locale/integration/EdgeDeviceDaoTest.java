package it.uniupo.pissir.darts.locale.integration;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code EdgeDeviceDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class EdgeDeviceDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private EdgeDeviceDao edgeDeviceDao;
    private DartboardDao dartboardDao;
    private long localeId;
    private long dartboardId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        LocaleDao localeDao = new LocaleDao(connectionProvider);
        dartboardDao = new DartboardDao(connectionProvider);
        edgeDeviceDao = new EdgeDeviceDao(connectionProvider);

        localeId = localeDao.insert(new Locale(0, "Bar Belvedere", "Via Test", LocaleType.PUBBLICO, 3L,
                LocaleStatus.APPROVATO, Instant.now())).id();
        dartboardId = dartboardDao.insert(new Dartboard(0, localeId, "B1", 5L, DartboardStatus.DISPONIBILE,
                Instant.now())).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip nato offline senza segnale di presenza. 
     */
    @Test
    void insertEFindById_roundTrip_natoOfflineSenzaHeartbeat() {
        EdgeDevice inserito = edgeDeviceDao.insert(nuovoDispositivo("SN-001"));

        EdgeDevice rileto = edgeDeviceDao.findById(inserito.id()).orElseThrow();
        assertEquals(EdgeDeviceStatus.OFFLINE, rileto.stato());
        assertNull(rileto.ultimoHeartbeat());
        assertEquals(0, rileto.tiriInCoda());
    }

    /**
     * Verifica {@code findByDartboardId} nel caso trova il dispositivo del bersaglio. 
     */
    @Test
    void findByDartboardId_trovaIlDispositivoDelBersaglio() {
        edgeDeviceDao.insert(nuovoDispositivo("SN-001"));

        assertTrue(edgeDeviceDao.findByDartboardId(dartboardId).isPresent());
    }

    /**
     * Verifica {@code updateHeartbeatForDartboard} nel caso aggiorna solo il bersaglio. 
     */
    @Test
    void updateHeartbeatForDartboard_aggiornaSoloIlBersaglio() {
        EdgeDevice inserito = edgeDeviceDao.insert(nuovoDispositivo("SN-001"));
        Instant now = Instant.now();

        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, now, 3, EdgeDeviceStatus.ONLINE);
            return null;
        });

        EdgeDevice rileto = edgeDeviceDao.findById(inserito.id()).orElseThrow();
        assertEquals(EdgeDeviceStatus.ONLINE, rileto.stato());
        assertEquals(3, rileto.tiriInCoda());
    }

    /**
     * Verifica {@code findOnline} nel caso restituisce solo i dispositivi online. 
     */
    @Test
    void findOnline_restituisceSoloIDispositiviOnline() {
        EdgeDevice inserito = edgeDeviceDao.insert(nuovoDispositivo("SN-001"));
        connectionProvider.withTransaction(connection -> {
            edgeDeviceDao.updateHeartbeatForDartboard(connection, localeId, dartboardId, Instant.now(), 0, EdgeDeviceStatus.ONLINE);
            return null;
        });

        List<EdgeDevice> online = edgeDeviceDao.findOnline();

        assertEquals(1, online.size());
        assertEquals(inserito.id(), online.get(0).id());
    }

    /**
     * Verifica {@code update} nel caso imposta device utente identificativo. 
     */
    @Test
    void update_impostaDeviceUserId() {
        EdgeDevice inserito = edgeDeviceDao.insert(nuovoDispositivo("SN-001"));
        EdgeDevice aggiornato = new EdgeDevice(inserito.id(), dartboardId, localeId, "SN-001", 30, 90, 2, null,
                null, 0, EdgeDeviceStatus.OFFLINE, 42L);

        edgeDeviceDao.update(aggiornato);

        assertEquals(42L, edgeDeviceDao.findById(inserito.id()).orElseThrow().deviceUserId());
    }

    /**
     * Verifica {@code delete} nel caso bersaglio cascada sul dispositivo edge. 
     */
    @Test
    void delete_bersaglio_cascadaSulDispositivoEdge() {
        edgeDeviceDao.insert(nuovoDispositivo("SN-001"));

        dartboardDao.delete(dartboardId);

        assertTrue(edgeDeviceDao.findByDartboardId(dartboardId).isEmpty());
    }

    /**
     * Verifica {@code countByLocale} nel caso conta i dispositivi del locale. 
     */
    @Test
    void countByLocale_contaIDispositiviDelLocale() {
        edgeDeviceDao.insert(nuovoDispositivo("SN-001"));

        assertEquals(1, edgeDeviceDao.countByLocale(localeId));
    }

    /**
     * Prepara il dispositivo edge con i dati richiesti dal caso di prova.
     *
     * @param serialNumber numero seriale del dispositivo
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.EdgeDevice} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    private EdgeDevice nuovoDispositivo(String serialNumber) {
        return new EdgeDevice(0, dartboardId, localeId, serialNumber, 30, 90, 2, null, null, 0,
                EdgeDeviceStatus.OFFLINE, null);
    }
}
