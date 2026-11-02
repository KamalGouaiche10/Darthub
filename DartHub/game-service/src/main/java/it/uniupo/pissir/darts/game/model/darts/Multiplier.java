package it.uniupo.pissir.darts.game.model.darts;

/**
 * Definisce i moltiplicatori ammessi per i settori del bersaglio. 
 */
public enum Multiplier {
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3),
    OUTER_BULL(25),
    INNER_BULL(50);

    private final int valore;

    /**
     * Inizializza {@code Multiplier} con i dati e le dipendenze indicati.
     *
     * @param valore valore numerico da elaborare
     *  
     */
    Multiplier(int valore) {
        this.valore = valore;
    }

    /**
     * Restituisce il valore numerico da elaborare.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    public int valore() {
        return valore;
    }
}
