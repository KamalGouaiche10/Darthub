package it.uniupo.pissir.darts.game.unit;

import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.game.model.darts.ScoreCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code ScoreCalculator} con casi di prova JUnit. 
 */
class ScoreCalculatorTest {

    /**
     * Verifica {@code punti} nel caso settore normale single double triple.
     *
     * @param settore settore del bersaglio colpito
     * @see it.uniupo.pissir.darts.game.model.darts.DartSector
     *  
     */
    @ParameterizedTest
    @EnumSource(value = DartSector.class, names = "BULL", mode = EnumSource.Mode.EXCLUDE)
    void punti_settoreNormale_singleDoubleTriple(DartSector settore) {
        assertEquals(settore.base(), ScoreCalculator.punti(settore, Multiplier.SINGLE).getAsInt());
        assertEquals(settore.base() * 2, ScoreCalculator.punti(settore, Multiplier.DOUBLE).getAsInt());
        assertEquals(settore.base() * 3, ScoreCalculator.punti(settore, Multiplier.TRIPLE).getAsInt());
    }

    /**
     * Verifica {@code punti} nel caso bull outer e inner. 
     */
    @Test
    void punti_bull_outerEInner() {
        assertEquals(25, ScoreCalculator.punti(DartSector.BULL, Multiplier.OUTER_BULL).getAsInt());
        assertEquals(50, ScoreCalculator.punti(DartSector.BULL, Multiplier.INNER_BULL).getAsInt());
    }

    /**
     * Verifica {@code punti} nel caso bull con single double triple invalido. 
     */
    @Test
    void punti_bullConSingleDoubleTriple_invalido() {
        assertTrue(ScoreCalculator.punti(DartSector.BULL, Multiplier.SINGLE).isEmpty());
        assertTrue(ScoreCalculator.punti(DartSector.BULL, Multiplier.DOUBLE).isEmpty());
        assertTrue(ScoreCalculator.punti(DartSector.BULL, Multiplier.TRIPLE).isEmpty());
    }

    /**
     * Verifica {@code punti} nel caso settore normale con outer o inner bull invalido. 
     */
    @Test
    void punti_settoreNormaleConOuterOInnerBull_invalido() {
        assertTrue(ScoreCalculator.punti(DartSector.S1, Multiplier.OUTER_BULL).isEmpty());
        assertTrue(ScoreCalculator.punti(DartSector.S20, Multiplier.INNER_BULL).isEmpty());
    }

    /**
     * Verifica {@code isDouble} nel caso double e inner bull vero. 
     */
    @Test
    void isDouble_doubleEInnerBull_true() {
        assertTrue(ScoreCalculator.isDouble(Multiplier.DOUBLE));
        assertTrue(ScoreCalculator.isDouble(Multiplier.INNER_BULL));
    }

    /**
     * Verifica {@code isDouble} nel caso single triple outer bull falso. 
     */
    @Test
    void isDouble_singleTripleOuterBull_false() {
        assertFalse(ScoreCalculator.isDouble(Multiplier.SINGLE));
        assertFalse(ScoreCalculator.isDouble(Multiplier.TRIPLE));
        assertFalse(ScoreCalculator.isDouble(Multiplier.OUTER_BULL));
    }

    /**
     * Verifica {@code punti} nel caso s20 triple 60. 
     */
    @Test
    void punti_s20Triple_60() {
        OptionalInt risultato = ScoreCalculator.punti(DartSector.S20, Multiplier.TRIPLE);
        assertEquals(60, risultato.getAsInt());
    }
}
