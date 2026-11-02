package it.uniupo.pissir.darts.match.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.model.Throw;
import it.uniupo.pissir.darts.match.model.ThrowOrigin;
import it.uniupo.pissir.darts.match.support.TestDatabaseSupport;
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
 * Verifica il comportamento di {@code ThrowDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class ThrowDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private ThrowDao throwDao;
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
        throwDao = new ThrowDao(connectionProvider);

        matchId = matchDao.insert(new Match(0, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, MatchStatus.IN_CORSO, 5,
                null, 0, Instant.now(), null)).id();
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
     * Verifica {@code insertEFindById} nel caso round trip. 
     */
    @Test
    void insertEFindById_roundTrip() {
        long id = connectionProvider.withTransaction(connection -> throwDao.insert(connection, matchId, participantId,
                "TIRO", DartSector.S20, Multiplier.SINGLE, null, null, 20, 1, Esito.VALIDO,
                Instant.now(), Instant.now(), ThrowOrigin.LIVE, 10));

        Throw rileto = throwDao.findById(id).orElseThrow();
        assertEquals(DartSector.S20, rileto.settore());
        assertEquals(20, rileto.punti());
        assertEquals(Esito.VALIDO, rileto.esito());
    }

    /**
     * Verifica {@code findRilevantiByMatch} nel caso esclude ignorati. 
     */
    @Test
    void findRilevantiByMatch_escludeIgnorati() {
        inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:00Z"));
        inserisciTiro(Esito.IGNORATO, Instant.parse("2026-01-01T10:00:01Z"));

        List<Throw> rilevanti = connectionProvider.withConnection(
                connection -> throwDao.findRilevantiByMatch(connection, matchId));

        assertEquals(1, rilevanti.size());
        assertEquals(Esito.VALIDO, rilevanti.get(0).esito());
    }

    /**
     * Verifica {@code insertIgnorandoDuplicati} nel caso stesso bersaglio e stesso timestamp scartato. 
     */
    @Test
    void insertIgnorandoDuplicati_stessoDartboardEStessoTimestamp_scartato() {
        Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");

        boolean primo = connectionProvider.withTransaction(connection -> throwDao.insertIgnorandoDuplicati(connection,
                matchId, participantId, "TIRO", DartSector.S20, Multiplier.SINGLE, null, null, 20, 1,
                Esito.IGNORATO, timestamp, Instant.now(), ThrowOrigin.OFFLINE, 10));
        boolean secondo = connectionProvider.withTransaction(connection -> throwDao.insertIgnorandoDuplicati(connection,
                matchId, participantId, "TIRO", DartSector.S1, Multiplier.TRIPLE, null, null, 3, 1,
                Esito.IGNORATO, timestamp, Instant.now(), ThrowOrigin.OFFLINE, 10));

        assertTrue(primo);
        assertFalse(secondo);
        assertEquals(1, throwDao.countByMatch(matchId));
    }

    /**
     * Verifica {@code annullaTurno} nel caso marca annullati solo i tiri del turno indicato. 
     */
    @Test
    void annullaTurno_marcaAnnullatiSoloITiriDelTurnoIndicato() {
        long id1 = inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:00Z"));
        long id2 = connectionProvider.withTransaction(connection -> throwDao.insert(connection, matchId, participantId,
                "TIRO", DartSector.S1, Multiplier.SINGLE, null, null, 1, 2, Esito.VALIDO,
                Instant.parse("2026-01-01T10:01:00Z"), Instant.now(), ThrowOrigin.LIVE, 10));

        connectionProvider.withTransaction(connection -> {
            throwDao.annullaTurno(connection, participantId, 1);
            return null;
        });

        assertTrue(throwDao.findById(id1).orElseThrow().annullato());
        assertFalse(throwDao.findById(id2).orElseThrow().annullato());
    }

    /**
     * Verifica {@code aggiornaEsitoRicalcolato} nel caso modifica turno esito e annullamento. 
     */
    @Test
    void aggiornaEsitoRicalcolato_modificaTurnoEsitoEAnnullamento() {
        long id = inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:00Z"));

        connectionProvider.withTransaction(connection -> {
            throwDao.aggiornaEsitoRicalcolato(connection, id, 3, Esito.BUST, true);
            return null;
        });

        Throw rileto = throwDao.findById(id).orElseThrow();
        assertEquals(3, rileto.numeroTurno());
        assertEquals(Esito.BUST, rileto.esito());
        assertTrue(rileto.annullato());
    }

    /**
     * Verifica {@code findAllByMatch} nel caso include anche gli ignorati. 
     */
    @Test
    void findAllByMatch_includeAncheGliIgnorati() {
        inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:00Z"));
        inserisciTiro(Esito.IGNORATO, Instant.parse("2026-01-01T10:00:01Z"));

        List<Throw> tutti = connectionProvider.withConnection(connection -> throwDao.findAllByMatch(connection, matchId));

        assertEquals(2, tutti.size());
    }

    /**
     * Verifica {@code countByMatch} nel caso conta i tiri della partita. 
     */
    @Test
    void countByMatch_contaITiriDellaPartita() {
        inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:00Z"));
        inserisciTiro(Esito.VALIDO, Instant.parse("2026-01-01T10:00:01Z"));

        assertEquals(2, throwDao.countByMatch(matchId));
    }

    /**
     * Inserisce tiro per il tiro.
     *
     * @param esito esito del tiro o della partita
     * @param timestampSensore istante registrato dal sensore
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.game.model.darts.Esito
     *  
     */
    private long inserisciTiro(Esito esito, Instant timestampSensore) {
        return connectionProvider.withTransaction(connection -> throwDao.insert(connection, matchId, participantId,
                "TIRO", DartSector.S20, Multiplier.SINGLE, null, null, 20, 1, esito,
                timestampSensore, Instant.now(), ThrowOrigin.LIVE, 10));
    }
}
