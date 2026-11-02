package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di validation nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class ValidationException extends DomainException {

    /**
     * Inizializza {@code ValidationException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public ValidationException(String messaggio) {
        super(400, "RICHIESTA_NON_VALIDA", messaggio);
    }

    /**
     * Inizializza {@code ValidationException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     * @param cause causa originale dell'errore
     *  
     */
    public ValidationException(String messaggio, Throwable cause) {
        super(400, "RICHIESTA_NON_VALIDA", messaggio, cause);
    }

    /**
     * Inizializza {@code ValidationException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public ValidationException(String codice, String messaggio) {
        super(400, codice, messaggio);
    }
}
