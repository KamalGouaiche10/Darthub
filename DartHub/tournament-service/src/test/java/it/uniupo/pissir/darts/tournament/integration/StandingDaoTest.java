package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Standing;
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

/**
 * Verifica il comportamento di {@code StandingDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class StandingDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private StandingDao standingDao;
    private long tournamentId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        TournamentDao tournamentDao = new TournamentDao(connectionProvider);
        standingDao = new StandingDao(connectionProvider);
        tournamentId = tournamentDao.insert(new Tournament(0, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE,
                FormulaTorneo.GIRONE, 3, 1, 0, Instant.now(), null, TournamentStatus.APERTO, null)).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code applicaRisultato} nel caso prima volta crea la riga. 
     */
    @Test
    void applicaRisultato_primaVolta_creaLaRiga() {
        connectionProvider.withTransaction(connection -> {
            standingDao.applicaRisultato(connection, tournamentId, 5L, 3, true);
            return null;
        });

        Standing standing = standingDao.findByTournament(tournamentId).get(0);
        assertEquals(3, standing.punti());
        assertEquals(1, standing.partiteGiocate());
        assertEquals(1, standing.vittorie());
    }

    /**
     * Verifica {@code applicaRisultato} nel caso due volte somma i delta. 
     */
    @Test
    void applicaRisultato_dueVolte_sommaIDelta() {
        connectionProvider.withTransaction(connection -> {
            standingDao.applicaRisultato(connection, tournamentId, 5L, 3, true);
            standingDao.applicaRisultato(connection, tournamentId, 5L, 0, false);
            return null;
        });

        Standing standing = standingDao.findByTournament(tournamentId).get(0);
        assertEquals(3, standing.punti());
        assertEquals(2, standing.partiteGiocate());
        assertEquals(1, standing.vittorie());
    }

    /**
     * Verifica {@code findByTournament} nel caso ordinato per punti poi vittorie poi partite giocate. 
     */
    @Test
    void findByTournament_ordinatoPerPuntiPoiVittoriePoiPartiteGiocate() {
        connectionProvider.withTransaction(connection -> {
            standingDao.applicaRisultato(connection, tournamentId, 5L, 3, true);
            standingDao.applicaRisultato(connection, tournamentId, 6L, 3, true);
            standingDao.applicaRisultato(connection, tournamentId, 6L, 0, false);
            return null;
        });

        List<Standing> classifica = standingDao.findByTournament(tournamentId);

        assertEquals(5L, classifica.get(0).participantId());
    }

    /**
     * Verifica {@code aggiornaPosizione} nel caso imposta la posizione in classifica. 
     */
    @Test
    void aggiornaPosizione_impostaLaPosizioneInClassifica() {
        connectionProvider.withTransaction(connection -> {
            standingDao.applicaRisultato(connection, tournamentId, 5L, 3, true);
            standingDao.aggiornaPosizione(connection, tournamentId, 5L, 1);
            return null;
        });

        Standing standing = standingDao.findByTournament(tournamentId).get(0);
        assertEquals(1, standing.posizione());
    }
}
