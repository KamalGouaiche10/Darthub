package it.uniupo.pissir.darts.game.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dao.SensorTypeDao;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.model.SensorModalita;
import it.uniupo.pissir.darts.game.model.SensorType;
import it.uniupo.pissir.darts.game.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code SensorTypeDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class SensorTypeDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private SensorTypeDao sensorTypeDao;
    private long gameTypeId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        GameTypeDao gameTypeDao = new GameTypeDao(connectionProvider);
        sensorTypeDao = new SensorTypeDao(connectionProvider);
        gameTypeId = gameTypeDao.insert(new GameType(0, "Freccette", null, "darts")).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip. 
     */
    @Test
    void insertEFindById_roundTrip() {
        SensorType inserito = sensorTypeDao.insert(
                new SensorType(0, gameTypeId, "Sensore punteggio", "{settore,moltiplicatore}", SensorModalita.REALE));

        SensorType rileto = sensorTypeDao.findById(inserito.id()).orElseThrow();
        assertEquals("Sensore punteggio", rileto.nome());
        assertEquals(SensorModalita.REALE, rileto.modalita());
    }

    /**
     * Verifica {@code insert} nel caso nome duplicato per lo stesso gioco conflict exception. 
     */
    @Test
    void insert_nomeDuplicatoPerLoStessoGioco_conflictException() {
        sensorTypeDao.insert(new SensorType(0, gameTypeId, "Sensore", "{a}", SensorModalita.REALE));

        ConflictException e = assertThrows(ConflictException.class, () -> sensorTypeDao.insert(
                new SensorType(0, gameTypeId, "Sensore", "{b}", SensorModalita.EMULATO)));
        assertEquals("SENSORE_DUPLICATO", e.codice());
    }

    /**
     * Verifica il caso find per gioco tipo e conteggio per gioco tipo. 
     */
    @Test
    void findByGameTypeECountByGameType() {
        sensorTypeDao.insert(new SensorType(0, gameTypeId, "Sensore1", "{a}", SensorModalita.REALE));
        sensorTypeDao.insert(new SensorType(0, gameTypeId, "Sensore2", "{b}", SensorModalita.EMULATO));

        assertEquals(2, sensorTypeDao.findByGameType(gameTypeId, Page.of(0, 20)).size());
        assertEquals(2, sensorTypeDao.countByGameType(gameTypeId));
    }

    /**
     * Verifica {@code update} nel caso modifica modalita. 
     */
    @Test
    void update_modificaModalita() {
        SensorType inserito = sensorTypeDao.insert(
                new SensorType(0, gameTypeId, "Sensore", "{a}", SensorModalita.REALE));

        sensorTypeDao.update(new SensorType(inserito.id(), gameTypeId, "Sensore", "{a}", SensorModalita.EMULATO));

        assertEquals(SensorModalita.EMULATO, sensorTypeDao.findById(inserito.id()).orElseThrow().modalita());
    }

    /**
     * Verifica {@code delete} nel caso gioco cascada sul sensore. 
     */
    @Test
    void delete_gioco_cascadaSulSensore() {
        sensorTypeDao.insert(new SensorType(0, gameTypeId, "Sensore", "{a}", SensorModalita.REALE));
        GameTypeDao gameTypeDao = new GameTypeDao(connectionProvider);

        gameTypeDao.delete(gameTypeId);

        assertTrue(sensorTypeDao.findByGameType(gameTypeId, Page.of(0, 20)).isEmpty());
    }
}
