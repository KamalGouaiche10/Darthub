package it.uniupo.pissir.darts.locale.dto;

import it.uniupo.pissir.darts.locale.model.EdgeConfig;

/**
 * Risposta di POST /edge-devices/register.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param deviceId identificativo del dispositivo
 * @param token token da utilizzare
 * @param config configurazione del componente
 * @see it.uniupo.pissir.darts.locale.model.EdgeConfig
 *  
 */
public record RegisterEdgeDeviceResponseDTO(long deviceId, String token, EdgeConfig config) {
}
