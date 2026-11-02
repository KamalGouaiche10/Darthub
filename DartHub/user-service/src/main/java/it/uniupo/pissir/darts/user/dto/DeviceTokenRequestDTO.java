package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di POST /users/device-token: chiamata interna da locale-service.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 * @param serialNumber numero seriale del dispositivo
 *  
 */
public record DeviceTokenRequestDTO(long localeId, long dartboardId, String serialNumber) {
}
