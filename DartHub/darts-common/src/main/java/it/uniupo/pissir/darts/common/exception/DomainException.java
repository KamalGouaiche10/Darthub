package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di domain nelle operazioni della piattaforma. 
 */
public abstract class DomainException extends RuntimeException {

    private final int status;
    private final String codice;

    /**
     * Inizializza {@code DomainException} con i dati e le dipendenze indicati.
     *
     * @param status codice di stato HTTP
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    protected DomainException(int status, String codice, String messaggio) {
        super(messaggio);
        this.status = status;
        this.codice = codice;
    }

    /**
     * Inizializza {@code DomainException} con i dati e le dipendenze indicati.
     *
     * @param status codice di stato HTTP
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     * @param cause causa originale dell'errore
     *  
     */
    protected DomainException(int status, String codice, String messaggio, Throwable cause) {
        super(messaggio, cause);
        this.status = status;
        this.codice = codice;
    }

    /**
     * Restituisce il codice di stato HTTP dell'errore.
     *
     * @return codice di stato HTTP
     *  
     */
    public int status() {
        return status;
    }

    /**
     * Restituisce il codice dell'errore.
     *
     * @return codice dell'errore
     *  
     */
    public String codice() {
        return codice;
    }
}
