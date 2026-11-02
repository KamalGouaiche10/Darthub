package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Standing;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.service.GameTypeClient;
import it.uniupo.pissir.darts.tournament.service.LocaleClient;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code TournamentService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentDao tournamentDao;
    @Mock
    private TournamentLocaleDao tournamentLocaleDao;
    @Mock
    private TournamentMatchDao tournamentMatchDao;
    @Mock
    private StandingDao standingDao;
    @Mock
    private GameTypeClient gameTypeClient;
    @Mock
    private LocaleClient localeClient;
    @Mock
    private MatchServiceClient matchServiceClient;

    private TournamentService tournamentService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        tournamentService = new TournamentService(tournamentDao, tournamentLocaleDao, tournamentMatchDao, standingDao,
                gameTypeClient, localeClient, matchServiceClient);
    }

    /**
     * Verifica {@code create} nel caso nome vuoto validation exception. 
     */
    @Test
    void create_nomeVuoto_validationException() {
        assertThrows(ValidationException.class, () -> tournamentService.create(" ", 1L, "INDIVIDUALE", null, null, null, null, null, null));
    }

    /**
     * Verifica {@code create} nel caso gioco tipo identificativo mancante validation exception. 
     */
    @Test
    void create_gameTypeIdMancante_validationException() {
        assertThrows(ValidationException.class, () -> tournamentService.create("Torneo", null, "INDIVIDUALE", null, null, null, null, null, null));
    }

    /**
     * Verifica {@code create} nel caso verifica esistenza gioco tipo e applica predefinito. 
     */
    @Test
    void create_verificaEsistenzaGameTypeEApplicaDefault() {
        when(tournamentDao.insert(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        String start = Instant.now().plusSeconds(3600).toString();
        String end = Instant.now().plusSeconds(7200).toString();
        Tournament creato = tournamentService.create("Torneo", 1L, "INDIVIDUALE", null, null, null, null, start, end);

        verify(gameTypeClient).requireExists(1L);
        assertEquals(FormulaTorneo.GIRONE, creato.formula());
        assertEquals(3, creato.puntiVittoria());
        assertEquals(TournamentStatus.APERTO, creato.stato());
        assertEquals(Instant.parse(start), creato.dataInizio());
        assertEquals(Instant.parse(end), creato.dataFine());
    }

    /**
     * Verifica {@code create} nel caso richiede data future e ordinate. 
     */
    @Test
    void create_richiedeDateFutureEOrdinate() {
        String past = Instant.now().minusSeconds(1).toString();
        String future = Instant.now().plusSeconds(3600).toString();
        assertThrows(ValidationException.class, () -> tournamentService.create("T", 1L, "INDIVIDUALE", null,
                null, null, null, null, future));
        assertThrows(ValidationException.class, () -> tournamentService.create("T", 1L, "INDIVIDUALE", null,
                null, null, null, past, future));
        assertThrows(ValidationException.class, () -> tournamentService.create("T", 1L, "INDIVIDUALE", null,
                null, null, null, future, future));
    }

    /**
     * Verifica {@code update} nel caso torneo chiuso conflict exception. 
     */
    @Test
    void update_torneoChiuso_conflictException() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.CHIUSO)));

        ConflictException e = assertThrows(ConflictException.class,
                () -> tournamentService.update(1L, "Nuovo nome", null, null, null));
        assertEquals("TORNEO_CHIUSO", e.codice());
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti. 
     */
    @Test
    void update_campiParziali_mantengonoIRestanti() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));

        Tournament aggiornato = tournamentService.update(1L, null, 5, null, null);

        assertEquals("Torneo", aggiornato.nome());
        assertEquals(5, aggiornato.puntiVittoria());
        assertEquals(1, aggiornato.puntiPareggio());
    }

    /**
     * Verifica {@code addLocale} nel caso verifica esistenza locale. 
     */
    @Test
    void addLocale_verificaEsistenzaLocale() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));

        tournamentService.addLocale(1L, 9L);

        verify(localeClient).requireExists(9L);
        verify(tournamentLocaleDao).insert(1L, 9L);
    }

    /**
     * Verifica {@code addMatch} nel caso gioco tipo diverso business regola exception. 
     */
    @Test
    void addMatch_gameTypeDiverso_businessRuleException() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));
        when(matchServiceClient.gameTypeIdOf(50L)).thenReturn(99L);

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> tournamentService.addMatch(1L, 50L));
        assertEquals("GIOCO_NON_COMPATIBILE", e.codice());
    }

    /**
     * Verifica {@code addMatch} nel caso gia presente non duplica. 
     */
    @Test
    void addMatch_giaPresente_nonDuplica() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));
        when(matchServiceClient.gameTypeIdOf(50L)).thenReturn(1L);
        when(tournamentMatchDao.findByTournamentAndMatch(1L, 50L))
                .thenReturn(Optional.of(new TournamentMatch(1L, 50L, 1, null)));

        tournamentService.addMatch(1L, 50L);

        verify(tournamentMatchDao, never()).insert(anyLong(), anyLong(), anyInt(), any());
    }

    /**
     * Verifica {@code chiudi} nel caso senza locali business regola exception. 
     */
    @Test
    void chiudi_senzaLocali_businessRuleException() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of());

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> tournamentService.chiudi(1L));
        assertEquals("TORNEO_SENZA_LOCALI", e.codice());
    }

    /**
     * Verifica {@code chiudi} nel caso partite non completate business regola exception. 
     */
    @Test
    void chiudi_partiteNonCompletate_businessRuleException() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(tournamentMatchDao.findByTournament(1L)).thenReturn(List.of(new TournamentMatch(1L, 50L, 1, null)));
        when(matchServiceClient.statoOf(50L)).thenReturn("IN_CORSO");

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> tournamentService.chiudi(1L));
        assertEquals("PARTITE_NON_COMPLETATE", e.codice());
    }

    /**
     * Verifica {@code chiudi} nel caso valido imposta vincitore e stato chiuso. 
     */
    @Test
    void chiudi_valido_impostaVincitoreEStatoChiuso() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(tournamentMatchDao.findByTournament(1L)).thenReturn(List.of(new TournamentMatch(1L, 50L, 1, null)));
        when(matchServiceClient.statoOf(50L)).thenReturn("COMPLETATA");
        when(standingDao.findByTournament(1L)).thenReturn(List.of(new Standing(1L, 7L, 9, 3, 3, 1)));

        Tournament chiuso = tournamentService.chiudi(1L);

        assertEquals(TournamentStatus.CHIUSO, chiuso.stato());
        assertEquals(7L, chiuso.vincitoreParticipantId());
    }

    /**
     * Verifica il caso chiudi eliminazione con doppia assenza finale non inventa vincitore. 
     */
    @Test
    void chiudiEliminazioneConDoppiaAssenzaFinaleNonInventaVincitore() {
        Tournament elimination = new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE,
                FormulaTorneo.ELIMINAZIONE, 3, 1, 0, Instant.now(), null, TournamentStatus.IN_CORSO, null);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(elimination));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(tournamentMatchDao.findByTournament(1L)).thenReturn(List.of(new TournamentMatch(1L, 50L, 1, 0)));
        when(matchServiceClient.statoOf(50L)).thenReturn("COMPLETATA");
        when(standingDao.findByTournament(1L)).thenReturn(List.of(new Standing(1L, 7L, 9, 3, 3, 1)));
        when(matchServiceClient.completedEvent(50L)).thenReturn(new it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO(
                "double-absence", 50L, 1L, 9L, 10L, "INDIVIDUALE", List.of(
                new it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO.Risultato(7L, null, 0, 1, false),
                new it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO.Risultato(8L, null, 0, 1, false))));

        Tournament closed = tournamentService.chiudi(1L);
        assertEquals(null, closed.vincitoreParticipantId());
    }

    /**
     * Verifica {@code chiudiScaduto} nel caso senza partite e locali chiude senza vincitore.
     *
     * @param stato stato da impostare o verificare
     * @see it.uniupo.pissir.darts.tournament.model.TournamentStatus
     *  
     */
    @ParameterizedTest
    @EnumSource(value = TournamentStatus.class, names = {"APERTO", "IN_CORSO"})
    void chiudiScaduto_senzaPartiteELocali_chiudeSenzaVincitore(TournamentStatus stato) {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        Tournament scaduto = torneoConFine(stato, now.minusSeconds(1));
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(scaduto));

        Tournament chiuso = tournamentService.chiudiScaduto(1L, now);

        assertEquals(TournamentStatus.CHIUSO, chiuso.stato());
        assertNull(chiuso.vincitoreParticipantId());
        assertEquals(now, chiuso.dataFineEffettiva());
        assertEquals(scaduto.dataFine(), chiuso.dataFine());
        assertEquals(scaduto.dataInizioEffettiva(), chiuso.dataInizioEffettiva());
        verify(tournamentDao).update(chiuso);
        verifyNoInteractions(tournamentLocaleDao, standingDao, matchServiceClient);
    }

    /**
     * Verifica {@code chiudiScaduto} nel caso non chiude prima della fine o senza scadenza. 
     */
    @Test
    void chiudiScaduto_nonChiudePrimaDellaFineOSenzaScadenza() {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        when(tournamentDao.findById(1L))
                .thenReturn(Optional.of(torneoConFine(TournamentStatus.IN_CORSO, now.plusSeconds(1))))
                .thenReturn(Optional.of(torneoConFine(TournamentStatus.IN_CORSO, null)));

        assertEquals(TournamentStatus.IN_CORSO, tournamentService.chiudiScaduto(1L, now).stato());
        assertEquals(TournamentStatus.IN_CORSO, tournamentService.chiudiScaduto(1L, now).stato());

        verify(tournamentDao, never()).update(any(Tournament.class));
        verifyNoInteractions(tournamentMatchDao, matchServiceClient);
    }

    /**
     * Verifica {@code chiudiScaduto} nel caso gia chiuso non riscrive la fine effettiva. 
     */
    @Test
    void chiudiScaduto_giaChiuso_nonRiscriveLaFineEffettiva() {
        Tournament chiuso = torneoConFine(TournamentStatus.CHIUSO, Instant.now().minusSeconds(1));
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(chiuso));

        assertEquals(chiuso, tournamentService.chiudiScaduto(1L, Instant.now()));

        verify(tournamentDao, never()).update(any(Tournament.class));
    }

    /**
     * Verifica {@code chiudiScaduto} nel caso con partite aperte non forza la chiusura. 
     */
    @Test
    void chiudiScaduto_conPartiteAperte_nonForzaLaChiusura() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(
                torneoConFine(TournamentStatus.IN_CORSO, Instant.now().minusSeconds(1))));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(tournamentMatchDao.findByTournament(1L)).thenReturn(List.of(new TournamentMatch(1L, 50L, 1, null)));
        when(matchServiceClient.statoOf(50L)).thenReturn("IN_CORSO");

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> tournamentService.chiudiScaduto(1L, Instant.now()));

        assertEquals("PARTITE_NON_COMPLETATE", e.codice());
        verify(tournamentDao, never()).update(any(Tournament.class));
    }

    /**
     * Verifica {@code chiudiScaduto} nel caso con partite completate assegna il vincitore. 
     */
    @Test
    void chiudiScaduto_conPartiteCompletate_assegnaIlVincitore() {
        Instant now = Instant.now();
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(
                torneoConFine(TournamentStatus.IN_CORSO, now)));
        when(tournamentLocaleDao.findLocaliIds(1L)).thenReturn(List.of(9L));
        when(tournamentMatchDao.findByTournament(1L)).thenReturn(List.of(new TournamentMatch(1L, 50L, 1, null)));
        when(matchServiceClient.statoOf(50L)).thenReturn("COMPLETATA");
        when(standingDao.findByTournament(1L)).thenReturn(List.of(new Standing(1L, 7L, 9, 3, 3, 1)));

        Tournament chiuso = tournamentService.chiudiScaduto(1L, now);

        assertEquals(TournamentStatus.CHIUSO, chiuso.stato());
        assertEquals(7L, chiuso.vincitoreParticipantId());
    }

    /**
     * Verifica {@code segnaInCorso} nel caso solo se aperto. 
     */
    @Test
    void segnaInCorso_soloSeAperto() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.IN_CORSO)));

        tournamentService.segnaInCorso(1L);

        verify(tournamentDao, never()).update(any(Tournament.class));
    }

    /**
     * Verifica {@code segnaInCorso} nel caso da aperto aggiorna. 
     */
    @Test
    void segnaInCorso_daAperto_aggiorna() {
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(torneo(TournamentStatus.APERTO)));

        tournamentService.segnaInCorso(1L);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentDao).update(captor.capture());
        assertEquals(TournamentStatus.IN_CORSO, captor.getValue().stato());
    }

    /**
     * Prepara il valore numerico utilizzato dal mock.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    /**
     * Prepara il valore intero utilizzato dal mock.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament torneo(TournamentStatus stato) {
        return new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE, FormulaTorneo.GIRONE, 3, 1, 0,
                Instant.now(), null, stato, null);
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param stato stato da impostare o verificare
     * @param fine fine dell'intervallo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament torneoConFine(TournamentStatus stato, Instant fine) {
        return new Tournament(1L, "Torneo", 1L, ModalitaTorneo.INDIVIDUALE, FormulaTorneo.GIRONE, 3, 1, 0,
                Instant.parse("2026-10-09T11:00:00Z"), fine, stato, null,
                stato == TournamentStatus.APERTO ? null : Instant.parse("2026-10-09T11:03:16Z"), null);
    }
}
