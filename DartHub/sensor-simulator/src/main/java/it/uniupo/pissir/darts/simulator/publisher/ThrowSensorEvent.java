package it.uniupo.pissir.darts.simulator.publisher;

/**
 * Payload di sensori/{localeId/{dartboardId}/tiro}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param timestamp istante dell'evento
 *  
 */
public record ThrowSensorEvent(String settore, String moltiplicatore, String timestamp) {
}
