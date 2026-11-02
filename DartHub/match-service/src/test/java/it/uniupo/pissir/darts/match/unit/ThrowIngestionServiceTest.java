package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;
import it.uniupo.pissir.darts.match.service.GameTypeClient;
import it.uniupo.pissir.darts.match.service.MatchLockRegistry;
import it.uniupo.pissir.darts.match.service.MatchResultService;
import it.uniupo.pissir.darts.match.service.PlayerNameResolver;
import it.uniupo.pissir.darts.match.service.ScoringRuleClient;
import it.uniupo.pissir.darts.match.service.TeamNameResolver;
import it.uniupo.pissir.darts.match.service.ThrowIngestionService;
import it.uniupo.pissir.darts.match.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code ThrowIngestionService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.model.ScoringRule
 *  
 */
@ExtendWith(MockitoExtension.class)
class ThrowIngestionServiceTest {

    private static final ScoringRule REGOLA_501 = new ScoringRule(1, 1, "501", Direzione.DECRESCENTE,
            501, 0, true, true, 3, "{}");
    private static final long MATCH_ID = 1L;

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private MatchDao matchDao;
    @Mock
    private MatchParticipantDao participantDao;
    @Mock
    private ThrowDao throwDao;
    @Mock
    private ScoringRuleClient scoringRuleClient;
    @Mock
    private GameTypeClient gameTypeClient;
    @Mock
    private MatchResultService matchResultService;
    @Mock
    private MatchEventPublisher matchEventPublisher;
    @Mock
    private EdgeCommandPublisher edgeCommandPublisher;
    @Mock
    private TeamNameResolver teamNameResolver;
    @Mock
    private PlayerNameResolver playerNameResolver;

    private ThrowIngestionService throwIngestionService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        throwIngestionService = new ThrowIngestionService(connectionProvider, matchDao, participantDao, throwDao,
                scoringRuleClient, gameTypeClient, matchResultService, matchEventPublisher, edgeCommandPublisher,
                new MatchLockRegistry(), teamNameResolver, playerNameResolver);
        lenient().when(matchDao.findById(MATCH_ID)).thenReturn(Optional.of(matchInCorso()));
        lenient().when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        lenient().when(gameTypeClient.fetchMotore(1L)).thenReturn("darts");
    }

    /**
     * Verifica {@code ingerisci} nel caso partita inesistente scartato. 
     */
    @Test
    void ingerisci_partitaInesistente_scartato() {
        when(matchDao.findById(99L)).thenReturn(Optional.empty());

        boolean accettato = throwIngestionService.ingerisci(evento(99L, 1L, "S20", "SINGLE"));

        assertFalse(accettato);
        org.mockito.Mockito.verifyNoInteractions(throwDao);
    }

    /**
     * Verifica {@code ingerisci} nel caso partita non in corso scartato. 
     */
    @Test
    void ingerisci_partitaNonInCorso_scartato() {
        when(matchDao.findById(MATCH_ID)).thenReturn(Optional.of(new Match(MATCH_ID, 10, 1, 1, 1,
                ModalitaPartita.INDIVIDUALE, MatchStatus.CREATA, 5, null, 0, null, null)));

        assertFalse(throwIngestionService.ingerisci(evento(MATCH_ID, 1L, "S20", "SINGLE")));
    }

    /**
     * Verifica {@code ingerisci} nel caso partecipante non attivo scartato. 
     */
    @Test
    void ingerisci_partecipanteNonAttivo_scartato() {
        when(participantDao.findAttiviByMatch(MATCH_ID)).thenReturn(List.of(partecipante(2L, 501)));

        assertFalse(throwIngestionService.ingerisci(evento(MATCH_ID, 1L, "S20", "SINGLE")));
    }

    /**
     * Verifica {@code ingerisci} nel caso non il turno del partecipante scartato. 
     */
    @Test
    void ingerisci_nonIlTurnoDelPartecipante_scartato() {
        when(participantDao.findAttiviByMatch(MATCH_ID)).thenReturn(List.of(partecipante(1L, 501), partecipante(2L, 501)));
        when(throwDao.findRilevantiByMatch(any(), org.mockito.ArgumentMatchers.eq(MATCH_ID))).thenReturn(List.of());

        boolean accettato = throwIngestionService.ingerisci(evento(MATCH_ID, 2L, "S20", "SINGLE"));

        assertFalse(accettato);
        verify(participantDao, never()).update(any(), any());
    }

    /**
     * Verifica {@code ingerisci} nel caso tiro valido aggiorna punteggio e pubblica. 
     */
    @Test
    void ingerisci_tiroValido_aggiornaPunteggioEPubblica() {
        when(participantDao.findAttiviByMatch(MATCH_ID)).thenReturn(List.of(partecipante(1L, 501), partecipante(2L, 501)));
        when(throwDao.findRilevantiByMatch(any(), org.mockito.ArgumentMatchers.eq(MATCH_ID))).thenReturn(List.of());

        boolean accettato = throwIngestionService.ingerisci(evento(MATCH_ID, 1L, "S20", "SINGLE"));

        assertTrue(accettato);
        ArgumentCaptor<MatchParticipant> captor = ArgumentCaptor.forClass(MatchParticipant.class);
        verify(participantDao).update(any(), captor.capture());
        assertEquals(481, captor.getValue().punteggioCorrente());
        verify(matchEventPublisher).publishThrowEvent(any());
        verify(edgeCommandPublisher).publishScore(anyLongEq(), anyLongEq(), any());
        verify(matchResultService, never()).chiudiSuConnessione(any(), any(), any(), any(), any());
    }

    /**
     * Verifica {@code ingerisci} nel caso vittoria chiude la partita e pubblica chiusura. 
     */
    @Test
    void ingerisci_vittoria_chiudeLaPartitaEPubblicaChiusura() {

        when(participantDao.findAttiviByMatch(MATCH_ID)).thenReturn(List.of(partecipante(1L, 40)));
        when(throwDao.findRilevantiByMatch(any(), org.mockito.ArgumentMatchers.eq(MATCH_ID))).thenReturn(List.of());
        when(matchResultService.chiudiSuConnessione(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new MatchResult(1L, MATCH_ID, 1L, 0, 1, true)));
        when(matchDao.findById(MATCH_ID))
                .thenReturn(Optional.of(matchInCorso()))
                .thenReturn(Optional.of(new Match(MATCH_ID, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE,
                        MatchStatus.COMPLETATA, 5, null, 0, Instant.now(), Instant.now())));

        boolean accettato = throwIngestionService.ingerisci(evento(MATCH_ID, 1L, "S20", "DOUBLE"));

        assertTrue(accettato);
        verify(matchResultService).chiudiSuConnessione(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(1L));
        verify(matchResultService).pubblicaChiusura(any(), any(), any());
    }

    /**
     * Prepara i dati di evento di gioco per il caso di prova.
     *
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.event.GameEvent} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    private static GameEvent evento(long matchId, long participantId, String settore, String moltiplicatore) {
        return GameEvent.grezzo("TIRO", Map.of("settore", settore, "moltiplicatore", moltiplicatore), Instant.now())
                .conMatchEPartecipante(matchId, participantId);
    }

    /**
     * Prepara la partita con i dati richiesti dal caso di prova.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private static Match matchInCorso() {
        return new Match(MATCH_ID, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, MatchStatus.IN_CORSO, 5, null, 0,
                Instant.now(), null);
    }

    /**
     * Prepara il partecipante alla partita con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del tiro
     * @param punteggio punteggio da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private static MatchParticipant partecipante(long id, int punteggio) {
        return new MatchParticipant(id, MATCH_ID, id, null, (int) id, punteggio, false);
    }

    /**
     * Prepara il confronto dell'argomento numerico nel mock.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    private static long anyLongEq() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

}
