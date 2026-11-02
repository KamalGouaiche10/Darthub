package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di forbidden nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class ForbiddenException extends DomainException {

    /**
     * Inizializza {@code ForbiddenException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public ForbiddenException(String messaggio) {
        super(403, "NON_AUTORIZZATO", messaggio);
    }

    /**
     * Inizializza {@code ForbiddenException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public ForbiddenException(String codice, String messaggio) {
        super(403, codice, messaggio);
    }
}
