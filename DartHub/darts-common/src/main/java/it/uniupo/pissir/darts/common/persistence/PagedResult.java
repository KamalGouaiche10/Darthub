package it.uniupo.pissir.darts.common.persistence;

import java.util.List;

/**
 * Raccoglie una pagina di elementi e i dati della paginazione.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param <T> tipo degli elementi gestiti
 * @param contenuto elementi contenuti nella pagina o nel messaggio
 * @param pagina indice della pagina dei risultati
 * @param dimensione numero massimo di elementi per pagina
 * @param totaleElementi numero totale di elementi
 * @param totalePagine numero complessivo delle pagine
 * @see java.util.List
 *  
 */
public record PagedResult<T>(List<T> contenuto, int pagina, int dimensione, long totaleElementi, int totalePagine) {

    /**
     * Costruisce e restituisce l'oggetto richiesto.
     *
     * @param <T> tipo degli elementi gestiti
     * @param contenuto elementi contenuti nella pagina o nel messaggio
     * @param page parametri di paginazione
     * @param totaleElementi numero totale di elementi
     * @return la pagina dei risultati e i dati di paginazione
     * @see java.util.List
     *  
     */
    public static <T> PagedResult<T> of(List<T> contenuto, Page page, long totaleElementi) {
        int totalePagine = page.size() == 0 ? 0 : (int) Math.ceil((double) totaleElementi / page.size());
        return new PagedResult<>(contenuto, page.page(), page.size(), totaleElementi, totalePagine);
    }
}
