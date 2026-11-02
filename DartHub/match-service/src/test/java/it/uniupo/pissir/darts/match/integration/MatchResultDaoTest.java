package it.uniupo.pissir.darts.match.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code MatchResultDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class MatchResultDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private MatchResultDao matchResultDao;
    private long matchId;
    private long participantId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        MatchDao matchDao = new MatchDao(connectionProvider);
        MatchParticipantDao participantDao = new MatchParticipantDao(connectionProvider);
        matchResultDao = new MatchResultDao(connectionProvider);

        matchId = matchDao.insert(new Match(0, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, MatchStatus.CREATA, 5,
                null, 0, null, null)).id();
        participantId = participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false)).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindByMatch} nel caso ordinato per posizione. 
     */
    @Test
    void insertEFindByMatch_ordinatoPerPosizione() {
        connectionProvider.withTransaction(connection -> {
            matchResultDao.insert(connection, matchId, participantId, 0, 1, true);
            return null;
        });

        List<MatchResult> risultati = matchResultDao.findByMatch(matchId);

        assertEquals(1, risultati.size());
        assertTrue(risultati.get(0).vincitore());
        assertEquals(1, risultati.get(0).posizione());
    }

    /**
     * Verifica {@code existsForMatch} nel caso falso quando nessun risultato. 
     */
    @Test
    void existsForMatch_falseQuandoNessunRisultato() {
        boolean esiste = connectionProvider.withTransaction(connection -> matchResultDao.existsForMatch(connection, matchId));

        assertFalse(esiste);
    }

    /**
     * Verifica {@code existsForMatch} nel caso vero dopo inserimento. 
     */
    @Test
    void existsForMatch_trueDopoInserimento() {
        connectionProvider.withTransaction(connection -> {
            matchResultDao.insert(connection, matchId, participantId, 0, 1, true);
            return null;
        });

        boolean esiste = connectionProvider.withTransaction(connection -> matchResultDao.existsForMatch(connection, matchId));

        assertTrue(esiste);
    }

    /**
     * Verifica {@code delete} nel caso partita cascada sui risultati. 
     */
    @Test
    void delete_partita_cascadaSuiRisultati() {
        connectionProvider.withTransaction(connection -> {
            matchResultDao.insert(connection, matchId, participantId, 0, 1, true);
            return null;
        });
        MatchDao matchDao = new MatchDao(connectionProvider);

        matchDao.delete(matchId);

        assertTrue(matchResultDao.findByMatch(matchId).isEmpty());
    }
}
