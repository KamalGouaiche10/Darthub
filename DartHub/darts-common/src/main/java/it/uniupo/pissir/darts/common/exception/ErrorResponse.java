package it.uniupo.pissir.darts.common.exception;

import java.time.Instant;

/**
 * Risposta di errore uniforme prodotta da ogni microservizio.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param timestamp istante dell'evento
 * @param status codice di stato HTTP
 * @param codice codice dell'errore
 * @param messaggio messaggio descrittivo
 * @param path percorso o indirizzo della risorsa
 *  
 */
public record ErrorResponse(String timestamp, int status, String codice, String messaggio, String path) {

    /**
     * Costruisce e restituisce l'oggetto richiesto.
     *
     * @param status codice di stato HTTP
     * @param codice codice dell'errore
     * @param messaggio messaggio descrittivo
     * @param path percorso o indirizzo della risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.exception.ErrorResponse} prodotto o recuperato
     *  
     */
    public static ErrorResponse of(int status, String codice, String messaggio, String path) {
        return new ErrorResponse(Instant.now().toString(), status, codice, messaggio, path);
    }
}
