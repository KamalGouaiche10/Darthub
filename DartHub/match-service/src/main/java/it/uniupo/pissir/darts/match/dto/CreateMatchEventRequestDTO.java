package it.uniupo.pissir.darts.match.dto;

import java.util.Map;

/**
 * Corpo di POST /partite/{id/tiri}: fallback REST all'MQTT per EDGE_DEVICE, generico per qualunque tipo di gioco.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 * @param tipo tipo della risorsa o dell'evento
 * @param payload contenuto del messaggio
 * @param timestampSensore istante registrato dal sensore
 * @see java.util.Map
 *  
 */
public record CreateMatchEventRequestDTO(Long participantId, String tipo, Map<String, Object> payload,
                                          String timestampSensore) {
}
