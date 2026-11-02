package it.uniupo.pissir.darts.game.unit;

import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.DartsRuleEngine;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.game.model.darts.ThrowOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifica il comportamento di {@code DartsRuleEngine} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.model.ScoringRule
 *  
 */
class DartsRuleEngineTest {

    private static final ScoringRule REGOLA_501 = new ScoringRule(1, 1, "501", Direzione.DECRESCENTE,
            501, 0, true, true, 3, "{}");

    /**
     * Verifica {@code residuo40} nel caso double20 vittoria. 
     */
    @Test
    void residuo40_double20_vittoria() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 40, 40, 0, DartSector.S20, Multiplier.DOUBLE);

        assertEsito(esito, Esito.VITTORIA, 40, 0, false, true);
    }

    /**
     * Verifica {@code residuo20} nel caso single s20 bust perchémanca il doppio. 
     */
    @Test
    void residuo20_singleS20_bustPerchéManca_ilDoppio() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 20, 20, 0, DartSector.S20, Multiplier.SINGLE);

        assertEsito(esito, Esito.BUST, 0, 20, true, false);
    }

    /**
     * Verifica {@code residuo50} nel caso single20 valido residuo30. 
     */
    @Test
    void residuo50_single20_validoResiduo30() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 50, 50, 0, DartSector.S20, Multiplier.SINGLE);

        assertEsito(esito, Esito.VALIDO, 20, 30, false, false);
    }

    /**
     * Verifica {@code residuo20} nel caso triple20 bust per sforamento ripristina a inizio turno. 
     */
    @Test
    void residuo20_triple20_bustPerSforamento_ripristinaAInizioTurno() {

        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 20, 60, 1, DartSector.S20, Multiplier.TRIPLE);

        assertEsito(esito, Esito.BUST, 0, 60, true, false);
    }

    /**
     * Verifica {@code residuo2} nel caso single1 bust lascerebbe residuo1. 
     */
    @Test
    void residuo2_single1_bustLascerebbeResiduo1() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 2, 2, 0, DartSector.S1, Multiplier.SINGLE);

        assertEsito(esito, Esito.BUST, 0, 2, true, false);
    }

    /**
     * Verifica {@code residuo32} nel caso double16 vittoria. 
     */
    @Test
    void residuo32_double16_vittoria() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 32, 32, 0, DartSector.S16, Multiplier.DOUBLE);

        assertEsito(esito, Esito.VITTORIA, 32, 0, false, true);
    }

    /**
     * Verifica {@code bullConTriple} nel caso ignorato. 
     */
    @Test
    void bullConTriple_ignorato() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 100, 100, 0, DartSector.BULL, Multiplier.TRIPLE);

        assertEsito(esito, Esito.IGNORATO, 0, 100, false, false);
    }

    /**
     * Verifica {@code quartoTiroNelTurno} nel caso max tre ignorato. 
     */
    @Test
    void quartoTiroNelTurno_maxTre_ignorato() {
        ThrowOutcome esito = DartsRuleEngine.valuta(REGOLA_501, 100, 100, 3, DartSector.S20, Multiplier.SINGLE);

        assertEsito(esito, Esito.IGNORATO, 0, 100, false, false);
    }

    /**
     * Verifica {@code bustSenzaAnnullaTurno} nel caso mantiene il punteggio corrente. 
     */
    @Test
    void bustSenzaAnnullaTurno_mantieneIlPunteggioCorrente() {
        ScoringRule regolaSenzaAnnullo = new ScoringRule(2, 1, "301-senza-annullo", Direzione.DECRESCENTE,
                301, 0, true, false, 3, "{}");

        ThrowOutcome esito = DartsRuleEngine.valuta(regolaSenzaAnnullo, 2, 2, 0, DartSector.S1, Multiplier.SINGLE);

        assertEquals(Esito.BUST, esito.esito());
        assertEquals(2, esito.punteggioResiduo());
        assertFalse(esito.turnoAnnullato());
    }

    /**
     * Verifica {@code direzioneCrescente} nel caso obiettivo raggiunto senza chiusura double richiesta vittoria. 
     */
    @Test
    void direzioneCrescente_obiettivoRaggiuntoSenzaChiusuraDoubleRichiesta_vittoria() {

        ScoringRule regolaCrescente = new ScoringRule(3, 1, "cricket-semplificato", Direzione.CRESCENTE,
                0, 20, false, true, 3, "{}");

        ThrowOutcome esito = DartsRuleEngine.valuta(regolaCrescente, 0, 0, 0, DartSector.S20, Multiplier.SINGLE);

        assertEquals(Esito.VITTORIA, esito.esito());
        assertEquals(20, esito.punteggioResiduo());
    }

    /**
     * Verifica {@code direzioneCrescente} nel caso sotto obiettivo valido. 
     */
    @Test
    void direzioneCrescente_sottoObiettivo_valido() {
        ScoringRule regolaCrescente = new ScoringRule(3, 1, "cricket-semplificato", Direzione.CRESCENTE,
                0, 100, false, true, 3, "{}");

        ThrowOutcome esito = DartsRuleEngine.valuta(regolaCrescente, 0, 0, 0, DartSector.S20, Multiplier.SINGLE);

        assertEquals(Esito.VALIDO, esito.esito());
        assertEquals(20, esito.punteggioResiduo());
    }

    /**
     * Confronta l'esito del tiro con i valori attesi dal test.
     *
     * @param risultato risultato da utilizzare
     * @param esitoAtteso esito previsto dal test
     * @param puntiApplicatiAttesi punti da assegnare secondo il test
     * @param punteggioResiduoAtteso punteggio residuo previsto dal test
     * @param turnoAnnullatoAtteso indicatore di annullamento del turno previsto dal test
     * @param vittoriaAttesa indicatore di vittoria previsto dal test
     * @see it.uniupo.pissir.darts.game.model.darts.ThrowOutcome
     *  
     */
    private static void assertEsito(ThrowOutcome risultato, Esito esitoAtteso, int puntiApplicatiAttesi,
                                     int punteggioResiduoAtteso, boolean turnoAnnullatoAtteso, boolean vittoriaAttesa) {
        assertEquals(esitoAtteso, risultato.esito(), risultato.motivo());
        assertEquals(puntiApplicatiAttesi, risultato.puntiApplicati());
        assertEquals(punteggioResiduoAtteso, risultato.punteggioResiduo());
        assertEquals(turnoAnnullatoAtteso, risultato.turnoAnnullato());
        assertEquals(vittoriaAttesa, risultato.vittoria());
    }
}
