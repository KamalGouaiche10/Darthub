package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di POST /auth/service-token (grant client-credentials, vedi AuthController).
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param clientId identificativo del client MQTT
 * @param clientSecret credenziale del client tecnico
 *  
 */
public record ServiceTokenRequestDTO(String clientId, String clientSecret) {
}
