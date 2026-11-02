package it.uniupo.pissir.darts.statistics.unit;

import it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO;
import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO;
import it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO;
import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import it.uniupo.pissir.darts.statistics.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code StatisticsAggregationService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class StatisticsAggregationServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private PlayerStatisticsDao playerStatisticsDao;
    @Mock
    private TeamStatisticsDao teamStatisticsDao;
    @Mock
    private LocaleStatisticsDao localeStatisticsDao;
    @Mock
    private GameStatisticsDao gameStatisticsDao;
    @Mock
    private GlobalStatisticsDao globalStatisticsDao;
    @Mock
    private PlayerVisibilityDao playerVisibilityDao;
    @Mock
    private LocaleVisibilityDao localeVisibilityDao;
    @Mock
    private ProcessedEventDao processedEventDao;

    private StatisticsAggregationService service;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        service = new StatisticsAggregationService(connectionProvider, playerStatisticsDao, teamStatisticsDao,
                localeStatisticsDao, gameStatisticsDao, globalStatisticsDao, playerVisibilityDao, localeVisibilityDao,
                processedEventDao);
    }

    /**
     * Verifica {@code gestisciPartitaCompletata} nel caso evento gia elaborato non aggiorna nulla. 
     */
    @Test
    void gestisciPartitaCompletata_eventoGiaElaborato_nonAggiornaNulla() {
        when(processedEventDao.giaElaborato(any(), eq("evt-1"))).thenReturn(true);

        service.gestisciPartitaCompletata(evento("evt-1", "INDIVIDUALE", List.of(
                new MatchCompletedEventDTO.Risultato(5L, null, 501, 1, true))));

        verify(playerStatisticsDao, never()).applicaRisultatoPartita(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
        verify(processedEventDao, never()).segnaElaborato(any(), any());
    }

    /**
     * Verifica {@code gestisciPartitaCompletata} nel caso individuale aggiorna giocatore statistics non squadra. 
     */
    @Test
    void gestisciPartitaCompletata_individuale_aggiornaPlayerStatisticsNonTeam() {
        MatchCompletedEventDTO.Risultato risultato = new MatchCompletedEventDTO.Risultato(5L, null, 501, 1, true);

        service.gestisciPartitaCompletata(evento("evt-2", "INDIVIDUALE", List.of(risultato)));

        verify(playerStatisticsDao).applicaRisultatoPartita(any(), eq(5L), eq(501), eq(true), any());
        verify(teamStatisticsDao, never()).applicaRisultatoPartita(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
        verify(localeStatisticsDao).registraGiocatore(any(), anyLong(), anyLong(), eq(5L));
        verify(processedEventDao).segnaElaborato(any(), eq("evt-2"));
    }

    /**
     * Verifica {@code gestisciPartitaCompletata} nel caso squadra aggiorna squadra statistics non giocatore. 
     */
    @Test
    void gestisciPartitaCompletata_squadra_aggiornaTeamStatisticsNonPlayer() {
        MatchCompletedEventDTO.Risultato risultato = new MatchCompletedEventDTO.Risultato(null, 20L, 501, 1, true);

        service.gestisciPartitaCompletata(evento("evt-3", "SQUADRA", List.of(risultato)));

        verify(teamStatisticsDao).applicaRisultatoPartita(any(), eq(20L), eq(501), eq(true), any());
        verify(playerStatisticsDao, never()).applicaRisultatoPartita(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    /**
     * Verifica {@code gestisciPartitaCompletata} nel caso ospite senza giocatore identificativo non aggiorna giocatore statistics ma conta la partita. 
     */
    @Test
    void gestisciPartitaCompletata_ospiteSenzaPlayerId_nonAggiornaPlayerStatisticsMaContaLaPartita() {
        MatchCompletedEventDTO.Risultato ospite = new MatchCompletedEventDTO.Risultato(null, null, 200, 2, false);

        service.gestisciPartitaCompletata(evento("evt-4", "INDIVIDUALE", List.of(ospite)));

        verify(playerStatisticsDao, never()).applicaRisultatoPartita(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
        verify(localeStatisticsDao, never()).registraGiocatore(any(), anyLong(), anyLong(), anyLong());
        verify(localeStatisticsDao).registraPartita(any(), anyLong(), anyLong(), any());
        verify(gameStatisticsDao).registraPartita(any(), anyLong(), eq(1));
        verify(globalStatisticsDao).incrementaPartiteTotali(any(), any());
    }

    /**
     * Verifica {@code gestisciTiro} nel caso ospite senza giocatore identificativo non aggiorna e non apre transazione. 
     */
    @Test
    void gestisciTiro_ospiteSenzaPlayerId_nonAggiornaENonApreTransazione() {
        GameEvent evento = new GameEvent("evt-5", 1L, 1L, null, null, "TIRO", java.util.Map.of(), 60,
                Instant.now(), "LIVE", "VALIDO");

        service.gestisciTiro(evento);

        verify(playerStatisticsDao, never()).aggiornaMigliorTiro(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(connectionProvider, never()).withTransaction(any());
    }

    /**
     * Verifica {@code gestisciTiro} nel caso con giocatore identificativo aggiorna miglior tiro. 
     */
    @Test
    void gestisciTiro_conPlayerId_aggiornaMigliorTiro() {
        GameEvent evento = new GameEvent("evt-6", 1L, 1L, 5L, null, "TIRO", java.util.Map.of(), 60,
                Instant.now(), "LIVE", "VALIDO");

        service.gestisciTiro(evento);

        verify(playerStatisticsDao).aggiornaMigliorTiro(any(), eq(5L), eq(60), any());
    }

    /**
     * Verifica {@code gestisciContoUtente} nel caso aggiorna globali e visibilita. 
     */
    @Test
    void gestisciContoUtente_aggiornaGlobaliEVisibilita() {
        service.gestisciContoUtente(new UserCountChangedEventDTO("evt-7", 5L, false));

        verify(globalStatisticsDao).applicaDeltaUtenti(any(), eq(-1), any());
        verify(playerVisibilityDao).setVisibile(any(), eq(5L), eq(false));
    }

    /**
     * Verifica {@code gestisciContoLocale} nel caso aggiorna globali e visibilita. 
     */
    @Test
    void gestisciContoLocale_aggiornaGlobaliEVisibilita() {
        service.gestisciContoLocale(new LocaleCountChangedEventDTO("evt-8", 9L, true));

        verify(globalStatisticsDao).applicaDeltaLocali(any(), eq(1), any());
        verify(localeVisibilityDao).setVisibile(any(), eq(9L), eq(true));
    }

    /**
     * Verifica {@code gestisciTorneoAggiornato} nel caso solo idempotenza non aggiorna altre tabelle. 
     */
    @Test
    void gestisciTorneoAggiornato_soloIdempotenza_nonAggiornaAltreTabelle() {
        service.gestisciTorneoAggiornato(new TournamentUpdatedEventDTO("evt-9", 1L, List.of()));

        verify(processedEventDao).segnaElaborato(any(), eq("evt-9"));
        verify(playerStatisticsDao, never()).applicaRisultatoPartita(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    /**
     * Prepara la partita con i dati richiesti dal caso di prova.
     *
     * @param eventId identificativo dell'evento
     * @param modalita modalità della partita o del sensore
     * @param risultati risultati dei partecipanti alla partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    private static MatchCompletedEventDTO evento(String eventId, String modalita, List<MatchCompletedEventDTO.Risultato> risultati) {
        return new MatchCompletedEventDTO(eventId, 50L, 1L, 9L, 10L, modalita, risultati);
    }
}
