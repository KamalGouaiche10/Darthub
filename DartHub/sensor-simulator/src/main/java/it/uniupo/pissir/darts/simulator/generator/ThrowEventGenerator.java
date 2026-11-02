package it.uniupo.pissir.darts.simulator.generator;

import java.util.Random;

/**
 * Genera settore e moltiplicatore dei tiri simulati.
 *
 * @see java.util.Random
 *  
 */
public final class ThrowEventGenerator {

    private static final double PROBABILITA_BULL = 0.03;
    private static final double PROBABILITA_OUTER_BULL_SU_BULL = 0.70;
    private static final double PROBABILITA_SINGLE = 0.60;
    private static final double PROBABILITA_SINGLE_PIU_TRIPLE = 0.85;

    private final Random random;

    /**
     * Inizializza {@code ThrowEventGenerator} con i dati e le dipendenze indicati.
     *
     * @param seed seme del generatore casuale
     *  
     */
    public ThrowEventGenerator(Long seed) {
        this.random = seed != null ? new Random(seed) : new Random();
    }

    /**
     * Genera te per il tiro.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.simulator.generator.GeneratedThrow} prodotto o recuperato
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    public GeneratedThrow generate() {
        if (random.nextDouble() < PROBABILITA_BULL) {
            Multiplier moltiplicatore = random.nextDouble() < PROBABILITA_OUTER_BULL_SU_BULL
                    ? Multiplier.OUTER_BULL : Multiplier.INNER_BULL;
            return new GeneratedThrow(DartSector.BULL, moltiplicatore);
        }
        DartSector settore = DartSector.SETTORI_NORMALI[random.nextInt(DartSector.SETTORI_NORMALI.length)];
        double r = random.nextDouble();
        Multiplier moltiplicatore;
        if (r < PROBABILITA_SINGLE) {
            moltiplicatore = Multiplier.SINGLE;
        } else if (r < PROBABILITA_SINGLE_PIU_TRIPLE) {
            moltiplicatore = Multiplier.TRIPLE;
        } else {
            moltiplicatore = Multiplier.DOUBLE;
        }
        return new GeneratedThrow(settore, moltiplicatore);
    }
}
