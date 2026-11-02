package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.BracketSlotDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.BracketSlot;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code TournamentMatchAndBracketDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class TournamentMatchAndBracketDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private TournamentMatchDao tournamentMatchDao;
    private BracketSlotDao bracketSlotDao;
    private ProcessedEventDao processedEventDao;
    private long tournamentId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        TournamentDao tournamentDao = new TournamentDao(connectionProvider);
        tournamentMatchDao = new TournamentMatchDao(connectionProvider);
        bracketSlotDao = new BracketSlotDao();
        processedEventDao = new ProcessedEventDao();
        tournamentId = tournamentDao.insert(new Tournament(0, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE,
                FormulaTorneo.ELIMINAZIONE, 3, 1, 0, Instant.now(), null, TournamentStatus.APERTO, null)).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code tournamentMatch} nel caso insert find per partita e esistono partite. 
     */
    @Test
    void tournamentMatch_insertFindByMatchEEsistonoPartite() {
        tournamentMatchDao.insert(tournamentId, 700L, 1, 0);

        assertTrue(tournamentMatchDao.esistonoPartite(tournamentId));
        TournamentMatch tm = tournamentMatchDao.findByMatch(700L).orElseThrow();
        assertEquals(tournamentId, tm.tournamentId());
        assertEquals(0, tm.slot());
    }

    /**
     * Verifica {@code tournamentMatch} nel caso delete tutti svuota il calendario. 
     */
    @Test
    void tournamentMatch_deleteAll_svuotaIlCalendario() {
        tournamentMatchDao.insert(tournamentId, 700L, 1, 0);
        tournamentMatchDao.insert(tournamentId, 701L, 1, 1);

        connectionProvider.withTransaction(connection -> {
            tournamentMatchDao.deleteAll(connection, tournamentId);
            return null;
        });

        assertFalse(tournamentMatchDao.esistonoPartite(tournamentId));
    }

    /**
     * Verifica {@code bracketSlot} nel caso upsert crea e poi aggiorna. 
     */
    @Test
    void bracketSlot_upsert_creaEPoiAggiorna() {
        connectionProvider.withTransaction(connection -> {
            bracketSlotDao.upsert(connection, tournamentId, 1, 0, 5L, null);
            return null;
        });
        BracketSlot creato = connectionProvider.withConnection(
                connection -> bracketSlotDao.find(connection, tournamentId, 1, 0).orElseThrow());
        assertEquals(5L, creato.participantId());

        connectionProvider.withTransaction(connection -> {
            bracketSlotDao.upsert(connection, tournamentId, 1, 0, 5L, 700L);
            return null;
        });
        BracketSlot aggiornato = connectionProvider.withConnection(
                connection -> bracketSlotDao.find(connection, tournamentId, 1, 0).orElseThrow());
        assertEquals(700L, aggiornato.matchId());
    }

    /**
     * Verifica {@code bracketSlot} nel caso find per turno e delete tutti. 
     */
    @Test
    void bracketSlot_findByTurno_eDeleteAll() {
        connectionProvider.withTransaction(connection -> {
            bracketSlotDao.upsert(connection, tournamentId, 1, 0, 5L, null);
            bracketSlotDao.upsert(connection, tournamentId, 1, 1, 6L, null);
            return null;
        });

        List<BracketSlot> slots = connectionProvider.withConnection(
                connection -> bracketSlotDao.findByTurno(connection, tournamentId, 1));
        assertEquals(2, slots.size());

        connectionProvider.withTransaction(connection -> {
            bracketSlotDao.deleteAll(connection, tournamentId);
            return null;
        });
        List<BracketSlot> dopo = connectionProvider.withConnection(
                connection -> bracketSlotDao.findByTurno(connection, tournamentId, 1));
        assertTrue(dopo.isEmpty());
    }

    /**
     * Verifica {@code processedEvent} nel caso idempotenza. 
     */
    @Test
    void processedEvent_idempotenza() {
        connectionProvider.withTransaction(connection -> {
            assertFalse(processedEventDao.giaElaborato(connection, "evt-1"));
            processedEventDao.segnaElaborato(connection, "evt-1");
            assertTrue(processedEventDao.giaElaborato(connection, "evt-1"));
            return null;
        });
    }
}
