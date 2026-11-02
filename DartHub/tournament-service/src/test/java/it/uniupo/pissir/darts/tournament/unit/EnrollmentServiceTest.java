package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.TipoIscrizione;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.service.EnrollmentService;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.service.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code EnrollmentService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.EnrollmentDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentDao enrollmentDao;
    @Mock
    private TournamentService tournamentService;
    @Mock
    private UserClient userClient;
    @Mock
    private MatchServiceClient matchServiceClient;

    private EnrollmentService enrollmentService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentDao, tournamentService, userClient, matchServiceClient);
    }

    /**
     * Verifica {@code enroll} nel caso partecipante identificativo mancante validation exception. 
     */
    @Test
    void enroll_participantIdMancante_validationException() {
        assertThrows(ValidationException.class, () -> enrollmentService.enroll(1L, null));
    }

    /**
     * Verifica {@code enroll} nel caso torneo non aperto conflict exception. 
     */
    @Test
    void enroll_torneoNonAperto_conflictException() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.IN_CORSO, ModalitaTorneo.INDIVIDUALE));

        ConflictException e = assertThrows(ConflictException.class, () -> enrollmentService.enroll(1L, 5L));
        assertEquals("TORNEO_NON_APERTO", e.codice());
    }

    /**
     * Verifica {@code enroll} nel caso aperto ma scaduto rifiuta anche amministratore. 
     */
    @Test
    void enroll_apertoMaScaduto_rifiutaAncheAdmin() {
        when(tournamentService.requireTournament(1L)).thenReturn(new Tournament(1L, "Scaduto", 1L,
                ModalitaTorneo.INDIVIDUALE, FormulaTorneo.GIRONE, 3, 1, 0,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600), TournamentStatus.APERTO, null));
        ConflictException error = assertThrows(ConflictException.class, () -> enrollmentService.enroll(1L, 5L));
        assertEquals("ISCRIZIONI_CHIUSE", error.codice());
        verify(enrollmentDao, never()).insert(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifica {@code enroll} nel caso individuale verifica esistenza giocatore. 
     */
    @Test
    void enroll_individuale_verificaEsistenzaGiocatore() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.APERTO, ModalitaTorneo.INDIVIDUALE));
        when(enrollmentDao.find(1L, 5L)).thenReturn(Optional.empty());

        when(enrollmentDao.insert(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(TipoIscrizione.GIOCATORE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(10L);

        Enrollment iscrizione = enrollmentService.enroll(1L, 5L);

        verify(userClient).requireExists(5L);
        assertEquals(TipoIscrizione.GIOCATORE, iscrizione.tipo());
    }

    /**
     * Verifica {@code enroll} nel caso squadra verifica esistenza squadra. 
     */
    @Test
    void enroll_squadra_verificaEsistenzaSquadra() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.APERTO, ModalitaTorneo.SQUADRA));
        when(enrollmentDao.find(1L, 20L)).thenReturn(Optional.empty());

        enrollmentService.enroll(1L, 20L);

        verify(matchServiceClient).requireTeamExists(20L);
        verify(userClient, never()).requireExists(org.mockito.ArgumentMatchers.anyLong());
    }

    /**
     * Verifica {@code enroll} nel caso gia iscritto conflict exception. 
     */
    @Test
    void enroll_giaIscritto_conflictException() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.APERTO, ModalitaTorneo.INDIVIDUALE));
        when(enrollmentDao.find(1L, 5L)).thenReturn(Optional.of(new Enrollment(1L, 1L, 5L, TipoIscrizione.GIOCATORE, Instant.now())));

        ConflictException e = assertThrows(ConflictException.class, () -> enrollmentService.enroll(1L, 5L));
        assertEquals("GIA_ISCRITTO", e.codice());
    }

    /**
     * Verifica {@code withdraw} nel caso non iscritto non found. 
     */
    @Test
    void withdraw_nonIscritto_notFound() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.APERTO, ModalitaTorneo.INDIVIDUALE));
        when(enrollmentDao.find(1L, 5L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> enrollmentService.withdraw(1L, 5L));
    }

    /**
     * Verifica {@code withdraw} nel caso valido elimina iscrizione. 
     */
    @Test
    void withdraw_valido_eliminaIscrizione() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(TournamentStatus.APERTO, ModalitaTorneo.INDIVIDUALE));
        when(enrollmentDao.find(1L, 5L)).thenReturn(Optional.of(new Enrollment(1L, 1L, 5L, TipoIscrizione.GIOCATORE, Instant.now())));

        enrollmentService.withdraw(1L, 5L);

        verify(enrollmentDao).delete(1L, 5L);
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param stato stato da impostare o verificare
     * @param modalita modalità della partita o del sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament torneo(TournamentStatus stato, ModalitaTorneo modalita) {
        return new Tournament(1L, "Torneo", 1L, modalita, FormulaTorneo.GIRONE, 3, 1, 0,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), stato, null);
    }
}
