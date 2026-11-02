package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di servizio unavailable nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class ServiceUnavailableException extends DomainException {

    /**
     * Inizializza {@code ServiceUnavailableException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public ServiceUnavailableException(String messaggio) {
        super(503, "SERVIZIO_NON_DISPONIBILE", messaggio);
    }
}
