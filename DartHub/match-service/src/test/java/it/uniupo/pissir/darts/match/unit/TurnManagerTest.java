package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.Throw;
import it.uniupo.pissir.darts.match.model.ThrowOrigin;
import it.uniupo.pissir.darts.match.service.TurnManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica il comportamento di {@code TurnManager} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.model.ScoringRule
 *  
 */
class TurnManagerTest {

    private static final ScoringRule REGOLA_501 = new ScoringRule(1, 1, "501", Direzione.DECRESCENTE,
            501, 0, true, true, 3, "{}");

    /**
     * Verifica {@code nessunTiro} nel caso inizia col primo partecipante. 
     */
    @Test
    void nessunTiro_iniziaColPrimoPartecipante() {
        List<MatchParticipant> attivi = List.of(partecipante(1, 501), partecipante(2, 501));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, List.of(), REGOLA_501);

        assertEquals(1, turno.participantId());
        assertEquals(1, turno.numeroTurno());
        assertEquals(0, turno.tiriGiaFattiNelTurno());
        assertEquals(501, turno.puntiInizioTurno());
    }

    /**
     * Verifica {@code nessunPartecipanteAttivo} nel caso illegal state. 
     */
    @Test
    void nessunPartecipanteAttivo_illegalState() {
        assertThrows(IllegalStateException.class, () -> TurnManager.calcolaTurnoCorrente(List.of(), List.of(), REGOLA_501));
    }

    /**
     * Verifica {@code dopoUnTiroValido} nel caso resta lo stesso turno fino a tre tiri. 
     */
    @Test
    void dopoUnTiroValido_restaLoStessoTurnoFinoATreTiri() {
        List<MatchParticipant> attivi = List.of(partecipante(1, 481), partecipante(2, 501));
        List<Throw> tiri = List.of(tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, tiri, REGOLA_501);

        assertEquals(1, turno.participantId());
        assertEquals(1, turno.numeroTurno());
        assertEquals(1, turno.tiriGiaFattiNelTurno());
        assertEquals(501, turno.puntiInizioTurno());
    }

    /**
     * Verifica {@code dopoTreTiriValidi} nel caso passa al prossimo partecipante. 
     */
    @Test
    void dopoTreTiriValidi_passaAlProssimoPartecipante() {
        List<MatchParticipant> attivi = List.of(partecipante(1, 441), partecipante(2, 501));
        List<Throw> tiri = List.of(
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, tiri, REGOLA_501);

        assertEquals(2, turno.participantId());
        assertEquals(1, turno.numeroTurno());
        assertEquals(0, turno.tiriGiaFattiNelTurno());
    }

    /**
     * Verifica {@code dopoUnBust} nel caso passa al prossimo partecipante anche con meno di tre tiri. 
     */
    @Test
    void dopoUnBust_passaAlProssimoPartecipanteAncheConMenoDiTreTiri() {
        List<MatchParticipant> attivi = List.of(partecipante(1, 501), partecipante(2, 501));
        List<Throw> tiri = List.of(tiro(1, 1, Esito.BUST, DartSector.S20, Multiplier.TRIPLE, 0));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, tiri, REGOLA_501);

        assertEquals(2, turno.participantId());
    }

    /**
     * Verifica {@code rotazioneCiclica} nel caso torna al primo dopo l ultimo con numero turno incrementato. 
     */
    @Test
    void rotazioneCiclica_tornaAlPrimoDopoLUltimo_conNumeroTurnoIncrementato() {

        List<MatchParticipant> attivi = List.of(partecipante(1, 441), partecipante(2, 441));
        List<Throw> tiri = List.of(
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(1, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, tiri, REGOLA_501);

        assertEquals(1, turno.participantId());
        assertEquals(2, turno.numeroTurno());
    }

    /**
     * Verifica {@code ultimoTiratoreNonPiuAttivo} nel caso ripartendo dal primo. 
     */
    @Test
    void ultimoTiratoreNonPiuAttivo_ripartendoDalPrimo() {

        List<MatchParticipant> attivi = List.of(partecipante(1, 501));
        List<Throw> tiri = List.of(
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20),
                tiro(2, 1, Esito.VALIDO, DartSector.S20, Multiplier.SINGLE, 20));

        TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, tiri, REGOLA_501);

        assertEquals(1, turno.participantId());
    }

    /**
     * Prepara il partecipante alla partita con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del turn manager
     * @param punteggio punteggio da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private static MatchParticipant partecipante(long id, int punteggio) {
        return new MatchParticipant(id, 100, id, null, (int) id, punteggio, false);
    }

    /**
     * Applica il tiro richiesto al sensore simulato.
     *
     * @param participantId identificativo del partecipante
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Throw} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Throw
     *  
     */
    private static Throw tiro(long participantId, int numeroTurno, Esito esito, DartSector settore,
                               Multiplier moltiplicatore, int punti) {
        return new Throw(0, 100, participantId, "TIRO", settore, moltiplicatore, null, null, punti, numeroTurno,
                esito, false, Instant.now(), Instant.now(), ThrowOrigin.LIVE, 1);
    }
}
