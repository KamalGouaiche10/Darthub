package it.uniupo.pissir.darts.common.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Definisce le operazioni di lettura e modifica delle entità persistenti.
 *
 * @param <T> tipo degli elementi gestiti
 * @param <ID> tipo dell'identificativo dell'entità
 *  
 */
public interface Dao<T, ID> {

    /**
     * Cerca l'entità per identificativo.
     *
     * @param id identificativo dell'entità
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    Optional<T> findById(ID id);

    /**
     * Legge una pagina di entità.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    List<T> findAll(Page page);

    /**
     * Conta entità presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    long count();

    /**
     * Inserisce l'entità nel database.
     *
     * @param entity entità da salvare
     * @return l'oggetto {@code T} prodotto o recuperato
     *  
     */
    T insert(T entity);

    /**
     * Aggiorna i dati dell'entità nel database.
     *
     * @param entity entità da salvare
     *  
     */
    void update(T entity);

    /**
     * Elimina l'entità identificato dalla richiesta.
     *
     * @param id identificativo dell'entità
     *  
     */
    void delete(ID id);
}
