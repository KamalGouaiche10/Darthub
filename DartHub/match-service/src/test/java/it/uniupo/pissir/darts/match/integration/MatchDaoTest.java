package it.uniupo.pissir.darts.match.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code MatchDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class MatchDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private MatchDao matchDao;
    private MatchParticipantDao participantDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        matchDao = new MatchDao(connectionProvider);
        participantDao = new MatchParticipantDao(connectionProvider);
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip nata in stato creata. 
     */
    @Test
    void insertEFindById_roundTrip_nataInStatoCreata() {
        Match inserita = matchDao.insert(nuovaPartita(10, 1, 5));

        Match rileta = matchDao.findById(inserita.id()).orElseThrow();
        assertEquals(MatchStatus.CREATA, rileta.stato());
        assertEquals(10, rileta.dartboardId());
    }

    /**
     * Verifica {@code findByLocalUuid} nel caso trova partita offline. 
     */
    @Test
    void findByLocalUuid_trovaPartitaOffline() {
        connectionProvider.withTransaction(connection -> matchDao.insertOffline(connection, 10, 1, 1, 1,
                ModalitaPartita.INDIVIDUALE, 5, "uuid-abc", 100, Instant.now()));

        Optional<Match> trovata = matchDao.findByLocalUuid("uuid-abc");

        assertTrue(trovata.isPresent());
        assertEquals(MatchStatus.SINCRONIZZATA, trovata.get().stato());
    }

    /**
     * Verifica {@code update} nel caso cambia stato e data. 
     */
    @Test
    void update_cambiaStatoEDate() {
        Match inserita = matchDao.insert(nuovaPartita(10, 1, 5));
        Match aggiornata = new Match(inserita.id(), inserita.dartboardId(), inserita.localeId(), inserita.gameTypeId(),
                inserita.scoringRuleId(), inserita.modalita(), MatchStatus.IN_CORSO, inserita.createdBy(),
                inserita.localUuid(), 0, Instant.now(), null);

        matchDao.update(aggiornata);

        assertEquals(MatchStatus.IN_CORSO, matchDao.findById(inserita.id()).orElseThrow().stato());
    }

    /**
     * Verifica {@code search} nel caso filtra per locale e stato. 
     */
    @Test
    void search_filtraPerLocaleEStato() {
        Match m1 = matchDao.insert(nuovaPartita(10, 1, 5));
        matchDao.insert(nuovaPartita(11, 2, 5));
        matchDao.update(new Match(m1.id(), m1.dartboardId(), m1.localeId(), m1.gameTypeId(), m1.scoringRuleId(),
                m1.modalita(), MatchStatus.IN_CORSO, m1.createdBy(), m1.localUuid(), 0, Instant.now(), null));

        List<Match> diLocale1 = matchDao.search(null, 1L, null, null, null, Page.of(0, 20));
        List<Match> inCorso = matchDao.search(null, null, MatchStatus.IN_CORSO, null, null, Page.of(0, 20));

        assertEquals(1, diLocale1.size());
        assertEquals(1, inCorso.size());
        assertEquals(m1.id(), inCorso.get(0).id());
    }

    /**
     * Verifica {@code search} nel caso filtra per giocatore identificativo anche via partecipante. 
     */
    @Test
    void search_filtraPerPlayerId_ancheViaPartecipante() {
        Match m1 = matchDao.insert(nuovaPartita(10, 1, 5));
        matchDao.insert(nuovaPartita(11, 1, 5));
        connectionProvider.withTransaction(connection -> participantDao.insert(connection, m1.id(), 7L, null, 1));

        List<Match> diPlayer7 = matchDao.search(7L, null, null, null, null, Page.of(0, 20));

        assertEquals(1, diPlayer7.size());
        assertEquals(m1.id(), diPlayer7.get(0).id());
    }

    /**
     * Verifica {@code delete} nel caso rimuove partita. 
     */
    @Test
    void delete_rimuovePartita() {
        Match inserita = matchDao.insert(nuovaPartita(10, 1, 5));

        matchDao.delete(inserita.id());

        assertTrue(matchDao.findById(inserita.id()).isEmpty());
    }

    /**
     * Verifica {@code delete} nel caso partita cascada sui partecipanti. 
     */
    @Test
    void delete_partita_cascadaSuiPartecipanti() {
        Match inserita = matchDao.insert(nuovaPartita(10, 1, 5));
        connectionProvider.withTransaction(connection -> participantDao.insert(connection, inserita.id(), 7L, null, 1));

        matchDao.delete(inserita.id());

        assertTrue(participantDao.findByMatch(inserita.id()).isEmpty());
    }

    /**
     * Prepara la partita con i dati richiesti dal caso di prova.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private static Match nuovaPartita(long dartboardId, long localeId, long createdBy) {
        return new Match(0, dartboardId, localeId, 1, 1, ModalitaPartita.INDIVIDUALE, MatchStatus.CREATA, createdBy,
                null, 0, null, null);
    }
}
