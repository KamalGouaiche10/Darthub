package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.TipoIscrizione;
import it.uniupo.pissir.darts.tournament.model.Tournament;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code EnrollmentDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class EnrollmentDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private EnrollmentDao enrollmentDao;
    private long tournamentId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        TournamentDao tournamentDao = new TournamentDao(connectionProvider);
        enrollmentDao = new EnrollmentDao(connectionProvider);
        tournamentId = tournamentDao.insert(new Tournament(0, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE,
                FormulaTorneo.GIRONE, 3, 1, 0, Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200), TournamentStatus.APERTO, null)).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFind} nel caso round trip. 
     */
    @Test
    void insertEFind_roundTrip() {
        enrollmentDao.insert(tournamentId, 5L, TipoIscrizione.GIOCATORE, Instant.now());

        var trovata = enrollmentDao.find(tournamentId, 5L);

        assertTrue(trovata.isPresent());
        assertEquals(TipoIscrizione.GIOCATORE, trovata.get().tipo());
    }

    /**
     * Verifica {@code findByTournament} nel caso ordinato per identificativo. 
     */
    @Test
    void findByTournament_ordinatoPerId() {
        enrollmentDao.insert(tournamentId, 5L, TipoIscrizione.GIOCATORE, Instant.now());
        enrollmentDao.insert(tournamentId, 6L, TipoIscrizione.GIOCATORE, Instant.now());

        List<Enrollment> iscritti = enrollmentDao.findByTournament(tournamentId);

        assertEquals(2, iscritti.size());
        assertEquals(5L, iscritti.get(0).participantId());
    }

    /**
     * Verifica {@code delete} nel caso rimuove iscrizione. 
     */
    @Test
    void delete_rimuoveIscrizione() {
        enrollmentDao.insert(tournamentId, 5L, TipoIscrizione.GIOCATORE, Instant.now());

        enrollmentDao.delete(tournamentId, 5L);

        assertTrue(enrollmentDao.find(tournamentId, 5L).isEmpty());
    }
}
