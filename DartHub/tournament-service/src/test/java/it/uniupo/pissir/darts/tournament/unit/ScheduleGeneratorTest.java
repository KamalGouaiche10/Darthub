package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.BracketSlotDao;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.TipoIscrizione;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.service.LocaleClient;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code ScheduleGenerator} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class ScheduleGeneratorTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private TournamentLocaleDao tournamentLocaleDao;
    @Mock
    private TournamentMatchDao tournamentMatchDao;
    @Mock
    private BracketSlotDao bracketSlotDao;
    @Mock
    private EnrollmentDao enrollmentDao;
    @Mock
    private LocaleClient localeClient;
    @Mock
    private MatchServiceClient matchServiceClient;
    @Mock
    private TournamentService tournamentService;

    private ScheduleGenerator scheduleGenerator;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        scheduleGenerator = new ScheduleGenerator(connectionProvider, tournamentLocaleDao, tournamentMatchDao,
                bracketSlotDao, enrollmentDao, localeClient, matchServiceClient, tournamentService);
        lenient().when(localeClient.unBersaglioCompatibile(anyLong(), anyLong())).thenReturn(500L);
        lenient().when(matchServiceClient.creaPartita(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(700L, 701L, 702L, 703L, 704L, 705L);
    }

    /**
     * Verifica {@code generaCalendario} nel caso senza locali business regola exception. 
     */
    @Test
    void generaCalendario_senzaLocali_businessRuleException() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.GIRONE));
        when(enrollmentDao.findByTournament(1L)).thenReturn(List.of(iscrizione(5L), iscrizione(6L)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of());

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> scheduleGenerator.generaCalendario(1L, false));
        assertEquals("TORNEO_SENZA_LOCALI", e.codice());
    }

    /**
     * Verifica {@code generaCalendario} nel caso iscritti insufficienti business regola exception. 
     */
    @Test
    void generaCalendario_iscrittiInsufficienti_businessRuleException() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.GIRONE));
        when(enrollmentDao.findByTournament(1L)).thenReturn(List.of(iscrizione(5L)));

        assertThrows(BusinessRuleException.class, () -> scheduleGenerator.generaCalendario(1L, false));
    }

    /**
     * Verifica {@code generaCalendario} nel caso gia generato senza force conflict exception. 
     */
    @Test
    void generaCalendario_giaGenerato_senzaForce_conflictException() {
        Tournament started = new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE,
                FormulaTorneo.GIRONE, 3, 1, 0, Instant.now().minusSeconds(5), null,
                TournamentStatus.IN_CORSO, null);
        when(tournamentService.requireTournament(1L)).thenReturn(started);

        assertEquals(List.of(), scheduleGenerator.generaCalendario(1L, false));
        verify(matchServiceClient, never()).creaPartita(anyLong(), anyLong(), anyLong(), any(), any());
    }

    /**
     * Verifica {@code generaCalendario} nel caso riprende una generazione interrotta. 
     */
    @Test
    void generaCalendario_riprendeUnaGenerazioneInterrotta() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.GIRONE));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(enrollmentDao.findByTournament(1L)).thenReturn(List.of(iscrizione(5L), iscrizione(6L)));
        when(matchServiceClient.creaPartita(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(700L);
        assertEquals(List.of(700L), scheduleGenerator.generaCalendario(1L, false));
        verify(tournamentMatchDao).insert(1L, 700L, 1, null);
    }

    /**
     * Verifica {@code generaCalendario} nel caso girone quattro iscritti genera sei partite. 
     */
    @Test
    void generaCalendario_girone_quattroIscritti_generaSeiPartite() {

        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.GIRONE));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L, 10L));
        when(enrollmentDao.findByTournament(1L)).thenReturn(
                List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L), iscrizione(4L)));

        List<Long> matchIds = scheduleGenerator.generaCalendario(1L, false);

        assertEquals(6, matchIds.size());
        verify(matchServiceClient, times(6)).creaPartita(anyLong(), anyLong(), eq(1L), eq("INDIVIDUALE"), any());
        verify(tournamentService).segnaInCorso(1L);
    }

    /**
     * Verifica {@code generaCalendario} nel caso girone tre iscritti dispari genera tre partite. 
     */
    @Test
    void generaCalendario_girone_treIscrittiDispari_generaTrePartite() {

        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.GIRONE));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(enrollmentDao.findByTournament(1L)).thenReturn(List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L)));

        List<Long> matchIds = scheduleGenerator.generaCalendario(1L, false);

        assertEquals(3, matchIds.size());
    }

    /**
     * Verifica {@code generaCalendario} nel caso eliminazione quattro iscritti nessun riposo genera due partite primo turno. 
     */
    @Test
    void generaCalendario_eliminazione_quattroIscritti_nessunBye_generaDuePartitePrimoTurno() {
        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.ELIMINAZIONE));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(enrollmentDao.findByTournament(1L)).thenReturn(
                List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L), iscrizione(4L)));

        List<Long> matchIds = scheduleGenerator.generaCalendario(1L, false);

        assertEquals(2, matchIds.size());
        verify(tournamentMatchDao).insert(any(), eq(1L), anyLong(), eq(1), eq(0));
        verify(tournamentMatchDao).insert(any(), eq(1L), anyLong(), eq(1), eq(1));
    }

    /**
     * Verifica {@code generaCalendario} nel caso eliminazione tre iscritti un riposo genera una sola partita subito. 
     */
    @Test
    void generaCalendario_eliminazione_treIscritti_unBye_generaUnaSolaPartitaSubito() {

        when(tournamentService.requireTournament(1L)).thenReturn(torneo(FormulaTorneo.ELIMINAZIONE));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(enrollmentDao.findByTournament(1L)).thenReturn(List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L)));

        List<Long> matchIds = scheduleGenerator.generaCalendario(1L, false);

        assertEquals(1, matchIds.size());
        verify(bracketSlotDao).upsert(any(), eq(1L), eq(2), eq(0), eq(1L), isNull());
        verify(tournamentMatchDao).insert(any(), eq(1L), anyLong(), eq(1), eq(1));
    }

    /**
     * Verifica {@code avanzaEliminazione} nel caso turno finale non propaga nulla. 
     */
    @Test
    void avanzaEliminazione_turnoFinale_nonPropagaNulla() {
        when(enrollmentDao.findByTournament(1L)).thenReturn(
                List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L), iscrizione(4L)));
        Tournament tournament = torneo(FormulaTorneo.ELIMINAZIONE);

        scheduleGenerator.avanzaEliminazione(null, tournament, 2, 0, 42L);

        verify(tournamentLocaleDao, never()).findLocaliIds(anyLong());
        verify(bracketSlotDao, never()).upsert(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    /**
     * Verifica {@code avanzaEliminazione} nel caso turno intermedio risolve il prossimo contestant. 
     */
    @Test
    void avanzaEliminazione_turnoIntermedio_risolveIlProssimoContestant() {
        when(enrollmentDao.findByTournament(1L)).thenReturn(
                List.of(iscrizione(1L), iscrizione(2L), iscrizione(3L), iscrizione(4L)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        Tournament tournament = torneo(FormulaTorneo.ELIMINAZIONE);

        scheduleGenerator.avanzaEliminazione(null, tournament, 1, 0, 42L);

        verify(bracketSlotDao).upsert(any(), eq(1L), eq(2), eq(0), eq(42L), isNull());
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param formula formula del torneo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament torneo(FormulaTorneo formula) {
        return new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE, formula, 3, 1, 0, Instant.now().minusSeconds(5), null,
                TournamentStatus.APERTO, null);
    }

    /**
     * Prepara la iscrizione al torneo con i dati richiesti dal caso di prova.
     *
     * @param participantId identificativo del partecipante
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Enrollment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Enrollment
     *  
     */
    private static Enrollment iscrizione(long participantId) {
        return new Enrollment(participantId, 1L, participantId, TipoIscrizione.GIOCATORE, Instant.now());
    }
}
