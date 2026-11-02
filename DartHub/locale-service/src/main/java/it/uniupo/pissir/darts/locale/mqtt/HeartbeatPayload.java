package it.uniupo.pissir.darts.locale.mqtt;

/**
 * Payload di edge/{localeId/heartbeat}: {timestamp, stato, tiriInCoda}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param timestamp istante dell'evento
 * @param stato stato da impostare o verificare
 * @param tiriInCoda numero di tiri in attesa di sincronizzazione
 *  
 */
public record HeartbeatPayload(String timestamp, String stato, int tiriInCoda) {
}
