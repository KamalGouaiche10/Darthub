package it.uniupo.pissir.darts.game.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code GameTypeDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class GameTypeDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private GameTypeDao gameTypeDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        gameTypeDao = new GameTypeDao(connectionProvider);
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
        GameType inserito = gameTypeDao.insert(new GameType(0, "Freccette", "Gioco delle freccette", "darts"));

        Optional<GameType> trovato = gameTypeDao.findById(inserito.id());

        assertTrue(trovato.isPresent());
        assertEquals("Freccette", trovato.get().nome());
        assertEquals("darts", trovato.get().motore());
    }

    /**
     * Verifica {@code insert} nel caso nome duplicato conflict exception. 
     */
    @Test
    void insert_nomeDuplicato_conflictException() {
        gameTypeDao.insert(new GameType(0, "Freccette", null, "darts"));

        ConflictException e = assertThrows(ConflictException.class,
                () -> gameTypeDao.insert(new GameType(0, "Freccette", null, "darts")));
        assertEquals("GIOCO_DUPLICATO", e.codice());
    }

    /**
     * Verifica {@code update} nel caso modifica nome descrizione motore. 
     */
    @Test
    void update_modificaNomeDescrizioneMotore() {
        GameType inserito = gameTypeDao.insert(new GameType(0, "Vecchio", "Vecchia desc", "darts"));

        gameTypeDao.update(new GameType(inserito.id(), "Nuovo", "Nuova desc", "altro-motore"));

        GameType rileto = gameTypeDao.findById(inserito.id()).orElseThrow();
        assertEquals("Nuovo", rileto.nome());
        assertEquals("altro-motore", rileto.motore());
    }

    /**
     * Verifica {@code delete} nel caso rimuove gioco. 
     */
    @Test
    void delete_rimuoveGioco() {
        GameType inserito = gameTypeDao.insert(new GameType(0, "Da eliminare", null, "darts"));

        gameTypeDao.delete(inserito.id());

        assertTrue(gameTypeDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code findAll} nel caso paginazione. 
     */
    @Test
    void findAll_paginazione() {
        for (int i = 0; i < 3; i++) {
            gameTypeDao.insert(new GameType(0, "Gioco" + i, null, "darts"));
        }

        assertEquals(2, gameTypeDao.findAll(Page.of(0, 2)).size());
        assertEquals(1, gameTypeDao.findAll(Page.of(1, 2)).size());
        assertEquals(3, gameTypeDao.count());
    }
}
