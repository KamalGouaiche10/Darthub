package it.uniupo.pissir.darts.simulator.generator;

/**
 * Un tiro generato, pronto per essere pubblicato o loggato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @see it.uniupo.pissir.darts.simulator.generator.DartSector
 *  
 */
public record GeneratedThrow(DartSector settore, Multiplier moltiplicatore) {

    /**
     * Calcola i punti del tiro dal settore e dal moltiplicatore.
     *
     * @return punti assegnati al tiro
     *  
     */
    public int punti() {
        if (settore == DartSector.BULL) {
            return moltiplicatore == Multiplier.INNER_BULL ? 50 : 25;
        }
        int base = Integer.parseInt(settore.name().substring(1));
        return switch (moltiplicatore) {
            case DOUBLE -> base * 2;
            case TRIPLE -> base * 3;
            default -> base;
        };
    }

    /**
     * Indica se il moltiplicatore rappresenta un doppio.
     *
     * @return {@code true} se il moltiplicatore rappresenta un doppio; {@code false} altrimenti
     *  
     */
    public boolean isDouble() {
        return moltiplicatore == Multiplier.DOUBLE || moltiplicatore == Multiplier.INNER_BULL;
    }
}
