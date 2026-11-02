package it.uniupo.pissir.darts.game.model.darts;

import java.util.OptionalInt;

/**
 * Calcola i punti di un tiro dal settore e dal moltiplicatore. 
 */
public final class ScoreCalculator {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private ScoreCalculator() {
    }

    /**
     * Calcola i punti del tiro dal settore e dal moltiplicatore.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @return i punti calcolati, oppure un risultato vuoto se la combinazione del tiro non è valida
     * @see java.util.OptionalInt
     *  
     */
    public static OptionalInt punti(DartSector settore, Multiplier moltiplicatore) {
        if (settore == DartSector.BULL) {
            if (moltiplicatore == Multiplier.OUTER_BULL) {
                return OptionalInt.of(25);
            }
            if (moltiplicatore == Multiplier.INNER_BULL) {
                return OptionalInt.of(50);
            }
            return OptionalInt.empty();
        }
        if (moltiplicatore == Multiplier.SINGLE || moltiplicatore == Multiplier.DOUBLE
                || moltiplicatore == Multiplier.TRIPLE) {
            return OptionalInt.of(settore.base() * moltiplicatore.valore());
        }
        return OptionalInt.empty();
    }

    /**
     * Indica se il moltiplicatore rappresenta un doppio.
     *
     * @param moltiplicatore moltiplicatore del settore
     * @return {@code true} se il moltiplicatore rappresenta un doppio; {@code false} altrimenti
     * @see it.uniupo.pissir.darts.game.model.darts.Multiplier
     *  
     */
    public static boolean isDouble(Multiplier moltiplicatore) {
        return moltiplicatore == Multiplier.DOUBLE || moltiplicatore == Multiplier.INNER_BULL;
    }
}
