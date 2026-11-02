package it.uniupo.pissir.darts.simulator.publisher;

/**
 * Payload di sensori/{localeId/{dartboardId}/stato}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param online indicatore di connessione del dispositivo
 * @param batteria livello della batteria del sensore
 *  
 */
public record StatoSensorEvent(boolean online, int batteria) {
}
