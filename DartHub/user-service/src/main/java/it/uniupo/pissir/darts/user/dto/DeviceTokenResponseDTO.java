package it.uniupo.pissir.darts.user.dto;

/**
 * Risposta di POST /users/device-token.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param deviceUserId identificativo dell'account tecnico del dispositivo
 * @param token token da utilizzare
 *  
 */
public record DeviceTokenResponseDTO(long deviceUserId, String token) {
}
