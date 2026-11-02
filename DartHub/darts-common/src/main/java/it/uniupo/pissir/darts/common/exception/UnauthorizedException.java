package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di unauthorized nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class UnauthorizedException extends DomainException {

    /**
     * Inizializza {@code UnauthorizedException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public UnauthorizedException(String messaggio) {
        super(401, "NON_AUTENTICATO", messaggio);
    }

    /**
     * Inizializza {@code UnauthorizedException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public UnauthorizedException(String codice, String messaggio) {
        super(401, codice, messaggio);
    }
}
