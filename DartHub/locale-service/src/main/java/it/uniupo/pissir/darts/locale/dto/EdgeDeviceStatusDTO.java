package it.uniupo.pissir.darts.locale.dto;

/**
 * Risposta di GET /edge-devices/{id/stato}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del dispositivo edge
 * @param stato stato da impostare o verificare
 * @param ultimoHeartbeat istante dell'ultimo segnale di presenza
 * @param tiriInCoda numero di tiri in attesa di sincronizzazione
 *  
 */
public record EdgeDeviceStatusDTO(long id, String stato, String ultimoHeartbeat, int tiriInCoda) {
}
