package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.service.MatchAppointmentService;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.service.StandingsService;
import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica il comportamento di {@code MatchAppointmentService} con casi di prova JUnit.
 *
 * @see java.time.Instant
 *  
 */
class MatchAppointmentServiceTest {
    private static final Instant AT = Instant.parse("2030-04-02T14:00:00Z");
    private final MatchAppointmentDao appointments = mock(MatchAppointmentDao.class);
    private final TournamentMatchDao matches = mock(TournamentMatchDao.class);
    private final TournamentService tournaments = mock(TournamentService.class);
    private final MatchServiceClient remote = mock(MatchServiceClient.class);
    private final TournamentMatch pairing = new TournamentMatch(1, 10, 1, null, 21L, 22L, 3L);

    /**
     * Prepara l'appuntamento della partita con i dati richiesti dal caso di prova.
     *
     * @param now istante corrente usato nei controlli
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService
     *  
     */
    private MatchAppointmentService service(Instant now) {
        return new MatchAppointmentService(appointments, matches, tournaments, remote,
                Clock.fixed(now, ZoneOffset.UTC), 5);
    }
    /**
     * Prepara l'appuntamento della partita con i dati richiesti dal caso di prova.
     *
     * @param home partecipante di casa
     * @param away partecipante ospite
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    private MatchAppointmentDao.Appointment appointment(boolean home, boolean away) {
        return new MatchAppointmentDao.Appointment(10, 1, 5, AT, 60,
                home ? AT.minusSeconds(100) : null, away ? AT.minusSeconds(100) : null, null, 0);
    }

    /**
     * Verifica il caso solo un pronto vince a tavolino dopo cinque minuti. 
     */
    @Test void soloUnProntoVinceATavolinoDopoCinqueMinuti() {
        when(appointments.due(any(), eq(5))).thenReturn(List.of(appointment(true, false)));
        when(appointments.claimAbsence(eq(10L), any(), eq(5))).thenReturn(new MatchAppointmentDao.Appointment(10, 1, 5, AT, 60, AT, null, "RISOLUZIONE", 0));
        when(remote.statoOf(10)).thenReturn("CREATA");
        when(matches.findByMatch(10)).thenReturn(Optional.of(pairing));
        service(AT.plusSeconds(301)).processDue();
        verify(remote).resolveAbsence(10, 21L);
        verify(appointments).setOutcome(10, "VITTORIA_A_TAVOLINO", 0);
        verify(remote, never()).startTournament(anyLong());
    }

    /**
     * Verifica il caso nessuno pronto produce doppia assenza. 
     */
    @Test void nessunoProntoProduceDoppiaAssenza() {
        when(appointments.due(any(), eq(5))).thenReturn(List.of(appointment(false, false)));
        when(appointments.claimAbsence(eq(10L), any(), eq(5))).thenReturn(new MatchAppointmentDao.Appointment(10, 1, 5, AT, 60, null, null, "RISOLUZIONE", 0));
        when(remote.statoOf(10)).thenReturn("CREATA");
        when(matches.findByMatch(10)).thenReturn(Optional.of(pairing));
        service(AT.plusSeconds(600)).processDue();
        verify(remote).resolveAbsence(10, null);
        verify(appointments).setOutcome(10, "DOPPIA_ASSENZA", 0);
    }

    /**
     * Verifica il caso entrambi pronti avviano solo tutti orario e guasto non da tavolino. 
     */
    @Test void entrambiProntiAvvianoSoloAllOrarioEGuastoNonDaTavolino() {
        when(appointments.due(any(), eq(5))).thenReturn(List.of(appointment(true, true)));
        when(remote.statoOf(10)).thenReturn("CREATA");
        doThrow(new IllegalStateException("guasto tecnico")).when(remote).startTournament(10);
        service(AT.plusSeconds(600)).processDue();
        verify(remote).startTournament(10);
        verify(remote, never()).resolveAbsence(anyLong(), any());
        verify(appointments, never()).setOutcome(anyLong(), anyString(), anyInt());
    }

    /**
     * Verifica il caso prima dell orario non si avvia poi entrambi pronti avviano. 
     */
    @Test void primaDellOrarioNonSiAvviaPoiEntrambiProntiAvviano() {
        when(appointments.due(any(), eq(5))).thenAnswer(inv -> {
            Instant now = inv.getArgument(0);
            return now.isBefore(AT) ? List.of() : List.of(appointment(true, true));
        });
        when(remote.statoOf(10)).thenReturn("CREATA");
        service(AT.minusSeconds(1)).processDue();
        verify(remote, never()).startTournament(anyLong());
        service(AT).processDue();
        verify(remote).startTournament(10);
        verify(appointments).setOutcome(10, "IN_CORSO", 0);
    }

    /**
     * Verifica il caso accesso richiede partecipante e finestra del link. 
     */
    @Test void accessoRichiedePartecipanteEFinestraDelLink() {
        when(appointments.find(10)).thenReturn(appointment(false, false));
        when(matches.findByMatch(10)).thenReturn(Optional.of(pairing));
        Tournament tournament = mock(Tournament.class);
        when(tournament.modalita()).thenReturn(ModalitaTorneo.INDIVIDUALE);
        when(tournaments.requireTournament(1)).thenReturn(tournament);
        when(remote.statoOf(10)).thenReturn("CREATA");
        assertThrows(ForbiddenException.class, () -> service(AT.minusSeconds(1)).access(10, 21));
        assertThrows(ForbiddenException.class, () -> service(AT).access(10, 99));
        assertTrue(service(AT).access(10, 21).linkAvailable());
        assertTrue(service(AT).access(10, 22).linkAvailable());
        assertThrows(ForbiddenException.class, () -> service(AT.plusSeconds(301)).access(10, 21));
        verify(remote, never()).startTournament(anyLong());
    }

    /**
     * Verifica il caso riavvio dopo avvio remoto non trasforma la partita in tavolino. 
     */
    @Test void riavvioDopoAvvioRemotoNonTrasformaLaPartitaInTavolino() {
        when(appointments.due(any(), eq(5))).thenReturn(List.of(appointment(true, true)));
        when(remote.statoOf(10)).thenReturn("IN_CORSO");
        service(AT.plusSeconds(310)).processDue();
        verify(appointments).setOutcome(10, "IN_CORSO", 0);
        verify(remote, never()).resolveAbsence(anyLong(), any());
    }

    /**
     * Verifica il caso partita virtuale completata recupera la classifica anche senza MQTT. 
     */
    @Test void partitaVirtualeCompletataRecuperaLaClassificaAncheSenzaMqtt() {
        var running = new MatchAppointmentDao.Appointment(10, 1, 5, AT, 60,
                AT.minusSeconds(100), AT.minusSeconds(100), "IN_CORSO", 0);
        when(appointments.due(any(), eq(5))).thenReturn(List.of(running));
        when(remote.statoOf(10)).thenReturn("COMPLETATA");
        MatchCompletedEventDTO event = new MatchCompletedEventDTO("again", 10, 1, 1, 5,
                "INDIVIDUALE", List.of());
        when(remote.completedEvent(10)).thenReturn(event);
        StandingsService standings = mock(StandingsService.class);
        MatchAppointmentService service = service(AT.plusSeconds(600));
        service.setStandingsService(standings);
        service.processDue();
        verify(appointments).setOutcome(10, "COMPLETATA", 0);
        verify(standings).gestisciCompletamento(event);
    }
}
