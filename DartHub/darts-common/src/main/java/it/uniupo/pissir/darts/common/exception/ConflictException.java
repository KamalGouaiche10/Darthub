package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di conflict nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class ConflictException extends DomainException {

    /**
     * Inizializza {@code ConflictException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public ConflictException(String messaggio) {
        super(409, "CONFLITTO", messaggio);
    }

    /**
     * Inizializza {@code ConflictException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     * @param cause causa originale dell'errore
     *  
     */
    public ConflictException(String messaggio, Throwable cause) {
        super(409, "CONFLITTO", messaggio, cause);
    }

    /**
     * Inizializza {@code ConflictException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public ConflictException(String codice, String messaggio) {
        super(409, codice, messaggio);
    }
}
