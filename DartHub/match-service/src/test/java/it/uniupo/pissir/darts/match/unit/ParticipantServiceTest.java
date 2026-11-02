package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dto.ParticipantDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.service.MatchService;
import it.uniupo.pissir.darts.match.service.ParticipantService;
import it.uniupo.pissir.darts.match.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code ParticipantService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private MatchParticipantDao participantDao;
    @Mock
    private MatchService matchService;

    private ParticipantService participantService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        participantService = new ParticipantService(connectionProvider, participantDao, matchService);
    }

    /**
     * Verifica {@code add} nel caso partita non creata conflict exception. 
     */
    @Test
    void add_partitaNonCreata_conflictException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.IN_CORSO, ModalitaPartita.INDIVIDUALE));

        ConflictException e = assertThrows(ConflictException.class, () -> participantService.add(1L, 5L, null));
        assertEquals("PARTITA_NON_MODIFICABILE", e.codice());
    }

    /**
     * Verifica {@code add} nel caso ne giocatore identificativo ne squadra identificativo validation exception. 
     */
    @Test
    void add_nePlayerIdNeTeamId_validationException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));

        assertThrows(ValidationException.class, () -> participantService.add(1L, null, null));
    }

    /**
     * Verifica {@code add} nel caso entrambi giocatore identificativo e squadra identificativo validation exception. 
     */
    @Test
    void add_entrambiPlayerIdETeamId_validationException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));

        assertThrows(ValidationException.class, () -> participantService.add(1L, 5L, 9L));
    }

    /**
     * Verifica {@code add} nel caso partita individuale con squadra identificativo business regola exception. 
     */
    @Test
    void add_partitaIndividualeConTeamId_businessRuleException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> participantService.add(1L, null, 9L));
        assertEquals("MODALITA_INCOERENTE", e.codice());
    }

    /**
     * Verifica {@code add} nel caso partita a squadre con giocatore identificativo business regola exception. 
     */
    @Test
    void add_partitaASquadreConPlayerId_businessRuleException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.SQUADRA));

        assertThrows(BusinessRuleException.class, () -> participantService.add(1L, 5L, null));
    }

    /**
     * Verifica {@code add} nel caso valido inserisce partecipante. 
     */
    @Test
    void add_valido_inseriscePartecipante() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));
        when(participantDao.prossimoOrdineTurno(any(), eq(1L))).thenReturn(1);
        when(participantDao.insert(any(), eq(1L), eq(5L), eq((Long) null), anyInt())).thenReturn(42L);

        ParticipantDTO dto = participantService.add(1L, 5L, null);

        assertEquals(42L, dto.id());
        assertEquals(5L, dto.playerId());
        assertEquals(0, dto.punteggioCorrente());
    }

    /**
     * Verifica {@code remove} nel caso partita non creata conflict exception. 
     */
    @Test
    void remove_partitaNonCreata_conflictException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.IN_CORSO, ModalitaPartita.INDIVIDUALE));

        assertThrows(ConflictException.class, () -> participantService.remove(1L, 7L));
    }

    /**
     * Verifica {@code remove} nel caso partecipante inesistente non found. 
     */
    @Test
    void remove_partecipanteInesistente_notFound() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));
        when(participantDao.findById(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> participantService.remove(1L, 7L));
    }

    /**
     * Verifica {@code remove} nel caso partecipante di altra partita validation exception. 
     */
    @Test
    void remove_partecipanteDiAltraPartita_validationException() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));
        when(participantDao.findById(7L)).thenReturn(Optional.of(new MatchParticipant(7L, 99L, 5L, null, 1, 0, false)));

        assertThrows(ValidationException.class, () -> participantService.remove(1L, 7L));
        verify(participantDao, never()).delete(7L);
    }

    /**
     * Verifica {@code remove} nel caso valido elimina partecipante. 
     */
    @Test
    void remove_valido_eliminaPartecipante() {
        when(matchService.requireMatch(1L)).thenReturn(match(MatchStatus.CREATA, ModalitaPartita.INDIVIDUALE));
        when(participantDao.findById(7L)).thenReturn(Optional.of(new MatchParticipant(7L, 1L, 5L, null, 1, 0, false)));

        participantService.remove(1L, 7L);

        verify(participantDao).delete(7L);
    }

    /**
     * Recupera la partita indicata dal servizio remoto.
     *
     * @param stato stato da impostare o verificare
     * @param modalita modalità della partita o del sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private static Match match(MatchStatus stato, ModalitaPartita modalita) {
        return new Match(1L, 10, 1, 1, 1, modalita, stato, 5, null, 0, null, null);
    }
}
