package it.uniupo.pissir.darts.common.persistence;

/**
 * Definisce indice e dimensione di una pagina di risultati.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param page parametri di paginazione
 * @param size numero massimo di elementi della pagina
 *  
 */
public record Page(int page, int size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /**
     * Inizializza {@code Page} con i dati e le dipendenze indicati.
     *
     * @param page parametri di paginazione
     * @param size numero massimo di elementi della pagina
     *  
     */
    public Page {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    /**
     * Costruisce e restituisce l'oggetto richiesto.
     *
     * @param page parametri di paginazione
     * @param size numero massimo di elementi della pagina
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.persistence.Page} prodotto o recuperato
     *  
     */
    public static Page of(Integer page, Integer size) {
        return new Page(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    /**
     * Restituisce il numero massimo di elementi della pagina.
     *
     * @return il valore numerico calcolato o letto
     *  
     */
    public int limit() {
        return size;
    }

    /**
     * Calcola lo scostamento del primo elemento della pagina.
     *
     * @return l'indice del primo elemento della pagina
     *  
     */
    public int offset() {
        return page * size;
    }
}
