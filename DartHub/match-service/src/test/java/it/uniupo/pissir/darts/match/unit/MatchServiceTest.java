package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.dto.MatchDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.service.DartboardStatusClient;
import it.uniupo.pissir.darts.match.service.MatchResultService;
import it.uniupo.pissir.darts.match.service.MatchService;
import it.uniupo.pissir.darts.match.service.PlayerNameResolver;
import it.uniupo.pissir.darts.match.service.ScoringRuleClient;
import it.uniupo.pissir.darts.match.service.TeamNameResolver;
import it.uniupo.pissir.darts.match.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code MatchService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.model.ScoringRule
 *  
 */
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final ScoringRule REGOLA_501 = new ScoringRule(1, 1, "501", Direzione.DECRESCENTE,
            501, 0, true, true, 3, "{}");

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private MatchDao matchDao;
    @Mock
    private MatchParticipantDao participantDao;
    @Mock
    private ThrowDao throwDao;
    @Mock
    private TeamNameResolver teamNameResolver;
    @Mock
    private ScoringRuleClient scoringRuleClient;
    @Mock
    private DartboardStatusClient dartboardStatusClient;
    @Mock
    private EdgeCommandPublisher edgeCommandPublisher;
    @Mock
    private MatchResultService matchResultService;
    @Mock
    private PlayerNameResolver playerNameResolver;

    private MatchService matchService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        matchService = new MatchService(connectionProvider, matchDao, participantDao, throwDao, teamNameResolver,
                scoringRuleClient, dartboardStatusClient, edgeCommandPublisher, matchResultService, playerNameResolver);
    }

    /**
     * Verifica {@code create} nel caso inserisce partita in stato creata. 
     */
    @Test
    void create_inseriscePartitaInStatoCreata() {
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(matchDao.insert(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            return new Match(1L, m.dartboardId(), m.localeId(), m.gameTypeId(), m.scoringRuleId(), m.modalita(),
                    MatchStatus.CREATA, m.createdBy(), null, 0, null, null);
        });

        MatchDTO dto = matchService.create(10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, 5);

        assertEquals("CREATA", dto.stato());
        assertEquals(5, dto.createdBy());
        verify(dartboardStatusClient).requireCompatible(10, 1, 1);
    }

    /**
     * Verifica {@code create} nel caso blocca regola di altro gioco. 
     */
    @Test
    void create_bloccaRegolaDiAltroGioco() {
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> matchService.create(10, 1, 2, 1, ModalitaPartita.INDIVIDUALE, 5));
        assertEquals("REGOLA_NON_COMPATIBILE", error.codice());
        verify(matchDao, never()).insert(any(Match.class));
    }

    /**
     * Verifica {@code create} nel caso blocca bersaglio di altro gioco o locale. 
     */
    @Test
    void create_bloccaBersaglioDiAltroGiocoOLocale() {
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        doThrow(new BusinessRuleException("BERSAGLIO_NON_COMPATIBILE", "Bersaglio errato"))
                .when(dartboardStatusClient).requireCompatible(10, 1, 1);
        assertThrows(BusinessRuleException.class,
                () -> matchService.create(10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, 5));
        verify(matchDao, never()).insert(any(Match.class));
    }

    /**
     * Verifica {@code create} nel caso con chiave torneo riusa la partita dopo riavvio. 
     */
    @Test
    void create_conChiaveTorneoRiusaLaPartitaDopoRiavvio() {
        String key = "tournament:3:5:6";
        when(matchDao.findByLocalUuid(key)).thenReturn(Optional.of(new Match(44L, 10L, 1L, 1L, 1L,
                ModalitaPartita.INDIVIDUALE, MatchStatus.CREATA, 0L, key, 0L, null, null)));
        MatchDTO result = matchService.create(10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, 0, key);
        assertEquals(44L, result.id());
        verify(matchDao, never()).insert(any(Match.class));
    }

    /**
     * Verifica {@code avvia} nel caso partita senza partecipanti business regola exception. 
     */
    @Test
    void avvia_partitaSenzaPartecipanti_businessRuleException() {
        Match creata = match(1L, MatchStatus.CREATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(creata));
        when(participantDao.findByMatch(1L)).thenReturn(List.of());

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> matchService.avvia(1L));
        assertEquals("PARTECIPANTI_INSUFFICIENTI", e.codice());
    }

    /**
     * Verifica {@code avvia} nel caso partita non in stato creata conflict exception. 
     */
    @Test
    void avvia_partitaNonInStatoCreata_conflictException() {
        Match inCorso = match(1L, MatchStatus.IN_CORSO);
        when(matchDao.findById(1L)).thenReturn(Optional.of(inCorso));

        ConflictException e = assertThrows(ConflictException.class, () -> matchService.avvia(1L));
        assertEquals("PARTITA_NON_AVVIABILE", e.codice());
    }

    /**
     * Verifica {@code avvia} nel caso valida imposta punteggio iniziale e marca bersaglio in uso. 
     */
    @Test
    void avvia_valida_impostaPunteggioInizialeEMarcaBersaglioInUso() {
        Match creata = match(1L, MatchStatus.CREATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(creata));
        MatchParticipant partecipante = new MatchParticipant(1L, 1L, 5L, null, 1, 0, false);
        when(participantDao.findByMatch(1L)).thenReturn(List.of(partecipante));
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(playerNameResolver.resolve(5L)).thenReturn("Mario");

        matchService.avvia(1L);

        verify(participantDao).update(any(), org.mockito.ArgumentMatchers.argThat(p -> p.punteggioCorrente() == 501));
        verify(dartboardStatusClient).setInUso(creata.dartboardId(), creata.localeId(), creata.gameTypeId());
        verify(edgeCommandPublisher).publishCommandOrThrow(anyLong(), anyLong(), any());
    }

    /**
     * Verifica il caso avvia torneo prenota il bersaglio e invia il comando al sensore. 
     */
    @Test
    void avviaTorneoPrenotaIlBersaglioEInviaIlComandoAlSensore() {
        Match creata = new Match(12L, 10L, 1L, 1L, 1L, ModalitaPartita.INDIVIDUALE,
                MatchStatus.CREATA, 99L, "tournament:1:5:6", 0, null, null);
        when(matchDao.findById(12L)).thenReturn(Optional.of(creata));
        when(matchDao.isSensorTournament(12L)).thenReturn(true);
        when(participantDao.findByMatch(12L)).thenReturn(List.of(
                new MatchParticipant(1L, 12L, 5L, null, 1, 0, false),
                new MatchParticipant(2L, 12L, 6L, null, 2, 0, false)));
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(playerNameResolver.resolve(5L)).thenReturn("Mario");
        when(playerNameResolver.resolve(6L)).thenReturn("Luca");

        MatchDTO result = matchService.avviaTorneo(12L);

        assertEquals("IN_CORSO", result.stato());
        assertEquals(true, result.sensorTournament());
        verify(dartboardStatusClient).setInUso(10L, 1L, 1L);
        verify(matchDao).markSensorStarted(any(), org.mockito.ArgumentMatchers.eq(12L));
        verify(edgeCommandPublisher).publishCommandRequired(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L), any());
    }

    /**
     * Verifica {@code avvia} nel caso pubblicazione MQTT fallita servizio unavailable. 
     */
    @Test
    void avvia_pubblicazioneMqttFallita_serviceUnavailable() {
        Match creata = match(1L, MatchStatus.CREATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(creata));
        MatchParticipant partecipante = new MatchParticipant(1L, 1L, 5L, null, 1, 0, false);
        when(participantDao.findByMatch(1L)).thenReturn(List.of(partecipante));
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(playerNameResolver.resolve(5L)).thenReturn("Mario");
        doThrow(new ServiceUnavailableException("broker non raggiungibile"))
                .when(edgeCommandPublisher).publishCommandOrThrow(anyLong(), anyLong(), any());

        assertThrows(ServiceUnavailableException.class, () -> matchService.avvia(1L));

        verify(participantDao).update(any(), org.mockito.ArgumentMatchers.argThat(p -> p.punteggioCorrente() == 501));
        verify(matchDao).update(any(), org.mockito.ArgumentMatchers.argThat(m -> m.stato() == MatchStatus.ANNULLATA));
        verify(dartboardStatusClient).setDisponibile(10L);
    }

    /**
     * Verifica {@code fine} nel caso partita non in corso conflict exception. 
     */
    @Test
    void fine_partitaNonInCorso_conflictException() {
        Match creata = match(1L, MatchStatus.CREATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(creata));

        ConflictException e = assertThrows(ConflictException.class, () -> matchService.fine(1L));
        assertEquals("PARTITA_NON_IN_CORSO", e.codice());
    }

    /**
     * Verifica {@code fine} nel caso valida chiude e pubblica. 
     */
    @Test
    void fine_valida_chiudeEPubblica() {
        Match inCorso = match(1L, MatchStatus.IN_CORSO);
        when(matchDao.findById(1L)).thenReturn(Optional.of(inCorso));
        when(participantDao.findAttiviByMatch(1L)).thenReturn(List.of());
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(matchResultService.chiudiSuConnessione(any(), any(), any(), any(), any())).thenReturn(List.of());

        matchService.fine(1L);

        verify(matchResultService).pubblicaChiusura(any(), any(), any());
    }

    /**
     * Verifica {@code annulla} nel caso partita gia completata conflict exception. 
     */
    @Test
    void annulla_partitaGiaCompletata_conflictException() {
        Match completata = match(1L, MatchStatus.COMPLETATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(completata));

        ConflictException e = assertThrows(ConflictException.class, () -> matchService.annulla(1L));
        assertEquals("PARTITA_NON_ANNULLABILE", e.codice());
    }

    /**
     * Verifica {@code annulla} nel caso partita creata non libera il bersaglio. 
     */
    @Test
    void annulla_partitaCreata_nonLiberaIlBersaglio() {

        Match creata = match(1L, MatchStatus.CREATA);
        when(matchDao.findById(1L)).thenReturn(Optional.of(creata));

        matchService.annulla(1L);

        verify(dartboardStatusClient, never()).setDisponibile(anyLong());
    }

    /**
     * Verifica {@code annullaBozza} nel caso non interrompe una partita in corso. 
     */
    @Test
    void annullaBozza_nonInterrompeUnaPartitaInCorso() {
        when(matchDao.findById(1L)).thenReturn(Optional.of(match(1L, MatchStatus.IN_CORSO)));

        ConflictException errore = assertThrows(ConflictException.class, () -> matchService.annullaBozza(1L));

        assertEquals("PARTITA_GIA_AVVIATA", errore.codice());
        verify(dartboardStatusClient, never()).setDisponibile(anyLong());
    }

    /**
     * Verifica {@code annulla} nel caso partita in corso la completa senza vincitore. 
     */
    @Test
    void annulla_partitaInCorso_laCompletaSenzaVincitore() {
        Match inCorso = match(1L, MatchStatus.IN_CORSO);
        when(matchDao.findById(1L)).thenReturn(Optional.of(inCorso));
        when(participantDao.findAttiviByMatch(1L)).thenReturn(List.of());
        when(scoringRuleClient.fetch(1L)).thenReturn(REGOLA_501);
        when(matchResultService.chiudiSuConnessione(any(), any(), any(), any(), any())).thenReturn(List.of());

        matchService.annulla(1L);

        verify(matchResultService).pubblicaChiusura(any(), any(), any());
    }

    /**
     * Verifica {@code requireMatch} nel caso inesistente non found. 
     */
    @Test
    void requireMatch_inesistente_notFound() {
        when(matchDao.findById(99L)).thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> matchService.requireMatch(99L));
        assertEquals("PARTITA_NON_TROVATA", e.codice());
    }

    /**
     * Recupera la partita indicata dal servizio remoto.
     *
     * @param id identificativo della partita
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private static Match match(long id, MatchStatus stato) {
        return new Match(id, 10, 1, 1, 1, ModalitaPartita.INDIVIDUALE, stato, 5, null, 0, null, null);
    }
}
