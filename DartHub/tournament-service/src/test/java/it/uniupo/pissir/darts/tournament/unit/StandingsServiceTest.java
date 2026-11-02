package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.tournament.dao.ProcessedMatchDao;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Standing;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.mqtt.TournamentEventPublisher;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.StandingsService;
import it.uniupo.pissir.darts.tournament.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code StandingsService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class StandingsServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private TournamentDao tournamentDao;
    @Mock
    private TournamentMatchDao tournamentMatchDao;
    @Mock
    private StandingDao standingDao;
    @Mock
    private ProcessedEventDao processedEventDao;
    @Mock
    private ProcessedMatchDao processedMatchDao;
    @Mock
    private ScheduleGenerator scheduleGenerator;
    @Mock
    private TournamentEventPublisher tournamentEventPublisher;

    private StandingsService standingsService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        standingsService = new StandingsService(connectionProvider, tournamentDao, tournamentMatchDao, standingDao,
                processedEventDao, processedMatchDao, scheduleGenerator, tournamentEventPublisher);
    }

    /**
     * Verifica {@code gestisciCompletamento} nel caso evento gia elaborato scartato senza pubblicare. 
     */
    @Test
    void gestisciCompletamento_eventoGiaElaborato_scartatoSenzaPubblicare() {
        when(processedEventDao.giaElaborato(any(), eq("evt-1"))).thenReturn(true);

        standingsService.gestisciCompletamento(evento("evt-1", 50L, List.of()));

        verify(tournamentEventPublisher, never()).publish(any());
        verify(tournamentMatchDao, never()).findByMatch(anyLong());
    }

    /**
     * Verifica {@code gestisciCompletamento} nel caso partita non di un torneo segna elaborato e non pubblica. 
     */
    @Test
    void gestisciCompletamento_partitaNonDiUnTorneo_segnaElaboratoENonPubblica() {
        when(processedEventDao.giaElaborato(any(), eq("evt-2"))).thenReturn(false);
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.empty());

        standingsService.gestisciCompletamento(evento("evt-2", 50L, List.of()));

        verify(processedEventDao).segnaElaborato(any(), eq("evt-2"));
        verify(tournamentEventPublisher, never()).publish(any());
    }

    /**
     * Verifica {@code gestisciCompletamento} nel caso torneo individuale applica punti e pubblica classifica. 
     */
    @Test
    void gestisciCompletamento_torneoIndividuale_applicaPuntiEPubblicaClassifica() {
        when(processedEventDao.giaElaborato(any(), eq("evt-3"))).thenReturn(false);
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, null)));
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(FormulaTorneo.GIRONE)));
        when(standingDao.findByTournament(any(), eq(1L))).thenReturn(
                List.of(new Standing(1L, 5L, 3, 1, 1, null), new Standing(1L, 6L, 0, 1, 0, null)));

        MatchCompletedEventDTO.Risultato vincitore = new MatchCompletedEventDTO.Risultato(5L, null, 501, 1, true);
        MatchCompletedEventDTO.Risultato perdente = new MatchCompletedEventDTO.Risultato(6L, null, 200, 2, false);
        standingsService.gestisciCompletamento(evento("evt-3", 50L, List.of(vincitore, perdente)));

        verify(standingDao).applicaRisultato(any(), eq(1L), eq(5L), eq(3), eq(true));
        verify(standingDao).applicaRisultato(any(), eq(1L), eq(6L), eq(0), eq(false));
        verify(standingDao).aggiornaPosizione(any(), eq(1L), eq(5L), eq(1));
        verify(standingDao).aggiornaPosizione(any(), eq(1L), eq(6L), eq(2));
        verify(processedEventDao).segnaElaborato(any(), eq("evt-3"));
        verify(tournamentEventPublisher).publish(any());
    }

    /**
     * Verifica {@code gestisciCompletamento} nel caso formula eliminazione con vincitore avanza il tabellone. 
     */
    @Test
    void gestisciCompletamento_formulaEliminazioneConVincitore_avanzaIlTabellone() {
        when(processedEventDao.giaElaborato(any(), eq("evt-4"))).thenReturn(false);
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, 0)));
        Tournament torneo = torneo(FormulaTorneo.ELIMINAZIONE);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo));
        when(standingDao.findByTournament(any(), eq(1L))).thenReturn(List.of());

        MatchCompletedEventDTO.Risultato vincitore = new MatchCompletedEventDTO.Risultato(5L, null, 501, 1, true);
        standingsService.gestisciCompletamento(evento("evt-4", 50L, List.of(vincitore)));

        verify(scheduleGenerator).avanzaEliminazione(any(), eq(torneo), eq(1), eq(0), eq(5L));
    }

    /**
     * Verifica {@code gestisciCompletamento} nel caso risultato ospite senza giocatore identificativo ignorato nella classifica. 
     */
    @Test
    void gestisciCompletamento_risultatoOspiteSenzaPlayerId_ignoratoNellaClassifica() {
        when(processedEventDao.giaElaborato(any(), eq("evt-5"))).thenReturn(false);
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, null)));
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(FormulaTorneo.GIRONE)));
        when(standingDao.findByTournament(any(), eq(1L))).thenReturn(List.of());

        MatchCompletedEventDTO.Risultato ospite = new MatchCompletedEventDTO.Risultato(null, null, 0, 1, true);
        standingsService.gestisciCompletamento(evento("evt-5", 50L, List.of(ospite)));

        verify(standingDao, never()).applicaRisultato(any(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * Verifica il caso evento ripubblicato con identificativo diverso non conta la partita due volte. 
     */
    @Test
    void eventoRipubblicatoConIdDiversoNonContaLaPartitaDueVolte() {
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, null)));
        when(processedMatchDao.exists(any(), eq(50L))).thenReturn(true);

        standingsService.gestisciCompletamento(evento("nuovo-evento", 50L,
                List.of(new MatchCompletedEventDTO.Risultato(5L, null, 0, 1, true))));

        verify(standingDao, never()).applicaRisultato(any(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(tournamentEventPublisher, never()).publish(any());
    }

    /**
     * Verifica il caso doppia assenza in eliminazione non inventa vincitore e propaga posizione nel tabellone vuoto. 
     */
    @Test
    void doppiaAssenzaInEliminazioneNonInventaVincitoreEPropagaSlotVuoto() {
        Tournament torneo = torneo(FormulaTorneo.ELIMINAZIONE);
        when(tournamentMatchDao.findByMatch(50L)).thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, 0)));
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo));
        when(standingDao.findByTournament(any(), eq(1L))).thenReturn(List.of());

        standingsService.gestisciCompletamento(evento("assenza", 50L, List.of(
                new MatchCompletedEventDTO.Risultato(5L, null, 0, 1, false),
                new MatchCompletedEventDTO.Risultato(6L, null, 0, 1, false))));

        verify(scheduleGenerator).avanzaEliminazioneSenzaVincitore(any(), eq(torneo), eq(1), eq(0), eq(50L));
        verify(scheduleGenerator, never()).avanzaEliminazione(any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
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
        return new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE, formula, 3, 1, 0, Instant.now(), null,
                TournamentStatus.IN_CORSO, null);
    }

    /**
     * Prepara la partita con i dati richiesti dal caso di prova.
     *
     * @param eventId identificativo dell'evento
     * @param matchId identificativo della partita
     * @param risultati risultati dei partecipanti alla partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    private static MatchCompletedEventDTO evento(String eventId, long matchId, List<MatchCompletedEventDTO.Risultato> risultati) {
        return new MatchCompletedEventDTO(eventId, matchId, 1L, 9L, 500L, "INDIVIDUALE", risultati);
    }
}
