package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di POST /edge-devices/register.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param dartboardId identificativo del bersaglio
 * @param serialNumber numero seriale del dispositivo
 * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
 * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
 * @param qos livello MQTT di qualità del servizio
 * @param urlBrokerLocale indirizzo del broker MQTT locale
 *  
 */
public record RegisterEdgeDeviceRequestDTO(Long dartboardId, String serialNumber, Integer intervalloHeartbeatS,
                                            Integer sogliaOfflineS, Integer qos, String urlBrokerLocale) {
}
