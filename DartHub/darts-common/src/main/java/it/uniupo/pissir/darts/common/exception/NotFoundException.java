package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di non found nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class NotFoundException extends DomainException {

    /**
     * Inizializza {@code NotFoundException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public NotFoundException(String messaggio) {
        super(404, "NON_TROVATO", messaggio);
    }

    /**
     * Inizializza {@code NotFoundException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public NotFoundException(String codice, String messaggio) {
        super(404, codice, messaggio);
    }
}
