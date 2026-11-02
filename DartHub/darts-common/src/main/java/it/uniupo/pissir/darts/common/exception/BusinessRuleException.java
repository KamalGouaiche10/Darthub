package it.uniupo.pissir.darts.common.exception;

/**
 * Segnala una condizione di business regola nelle operazioni della piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.exception.DomainException
 *  
 */
public class BusinessRuleException extends DomainException {

    /**
     * Inizializza {@code BusinessRuleException} con i dati e le dipendenze indicati.
     *
     * @param messaggio messaggio descrittivo
     *  
     */
    public BusinessRuleException(String messaggio) {
        super(422, "REGOLA_VIOLATA", messaggio);
    }

    /**
     * Inizializza {@code BusinessRuleException} con i dati e le dipendenze indicati.
     *
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     *  
     */
    public BusinessRuleException(String codice, String messaggio) {
        super(422, codice, messaggio);
    }
}
