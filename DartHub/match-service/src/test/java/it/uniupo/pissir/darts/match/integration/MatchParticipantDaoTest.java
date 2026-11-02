package it.uniupo.pissir.darts.match.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code MatchParticipantDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class MatchParticipantDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private MatchParticipantDao participantDao;
    private long matchId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        MatchDao matchDao = new MatchDao(connectionProvider);
        participantDao = new MatchParticipantDao(connectionProvider);
        matchId = matchDao.insert(new Match(0, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, MatchStatus.CREATA, 5,
                null, 0, null, null)).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code prossimoOrdineTurno} nel caso incrementa a ogni inserimento. 
     */
    @Test
    void prossimoOrdineTurno_incrementaAOgniInserimento() {
        int primo = connectionProvider.withTransaction(connection -> participantDao.prossimoOrdineTurno(connection, matchId));
        assertEquals(1, primo);

        connectionProvider.withTransaction(connection -> participantDao.insert(connection, matchId, 5L, null, primo));
        int secondo = connectionProvider.withTransaction(connection -> participantDao.prossimoOrdineTurno(connection, matchId));

        assertEquals(2, secondo);
    }

    /**
     * Verifica {@code insertEFindById} nel caso nato con punteggio zero e non ritirato. 
     */
    @Test
    void insertEFindById_natoConPunteggioZeroENonRitirato() {
        MatchParticipant inserito = participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false));

        MatchParticipant rileto = participantDao.findById(inserito.id()).orElseThrow();
        assertEquals(0, rileto.punteggioCorrente());
        assertEquals(false, rileto.ritirato());
    }

    /**
     * Verifica {@code findAttiviByMatch} nel caso esclude i ritirati. 
     */
    @Test
    void findAttiviByMatch_esclude_iRitirati() {
        MatchParticipant p1 = participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false));
        MatchParticipant p2 = participantDao.insert(new MatchParticipant(0, matchId, 6L, null, 2, 0, false));
        participantDao.update(new MatchParticipant(p2.id(), matchId, 6L, null, 2, 0, true));

        List<MatchParticipant> attivi = participantDao.findAttiviByMatch(matchId);

        assertEquals(1, attivi.size());
        assertEquals(p1.id(), attivi.get(0).id());
    }

    /**
     * Verifica {@code update} nel caso modifica punteggio e ritiro. 
     */
    @Test
    void update_modificaPunteggioERitiro() {
        MatchParticipant inserito = participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false));

        participantDao.update(new MatchParticipant(inserito.id(), matchId, 5L, null, 1, 321, true));

        MatchParticipant rileto = participantDao.findById(inserito.id()).orElseThrow();
        assertEquals(321, rileto.punteggioCorrente());
        assertEquals(true, rileto.ritirato());
    }

    /**
     * Verifica {@code delete} nel caso rimuove partecipante. 
     */
    @Test
    void delete_rimuovePartecipante() {
        MatchParticipant inserito = participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false));

        participantDao.delete(inserito.id());

        assertTrue(participantDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code findByMatch} nel caso ordinato per ordine turno. 
     */
    @Test
    void findByMatch_ordinatoPerOrdineTurno() {
        participantDao.insert(new MatchParticipant(0, matchId, 6L, null, 2, 0, false));
        participantDao.insert(new MatchParticipant(0, matchId, 5L, null, 1, 0, false));

        List<MatchParticipant> lista = participantDao.findByMatch(matchId);

        assertEquals(5L, lista.get(0).playerId());
        assertEquals(6L, lista.get(1).playerId());
    }
}
