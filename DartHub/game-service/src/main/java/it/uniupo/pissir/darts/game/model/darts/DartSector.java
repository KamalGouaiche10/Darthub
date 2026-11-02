package it.uniupo.pissir.darts.game.model.darts;

/**
 * Rappresenta i settori numerati e il centro del bersaglio. 
 */
public enum DartSector {
    S1(1), S2(2), S3(3), S4(4), S5(5), S6(6), S7(7), S8(8), S9(9), S10(10),
    S11(11), S12(12), S13(13), S14(14), S15(15), S16(16), S17(17), S18(18), S19(19), S20(20),

    BULL(25);

    private final int base;

    /**
     * Inizializza {@code DartSector} con i dati e le dipendenze indicati.
     *
     * @param base base da utilizzare
     *  
     */
    DartSector(int base) {
        this.base = base;
    }

    /**
     * Restituisce il base da utilizzare.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    public int base() {
        return base;
    }
}
