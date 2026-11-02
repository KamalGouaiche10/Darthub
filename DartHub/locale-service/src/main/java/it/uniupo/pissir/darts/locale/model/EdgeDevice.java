package it.uniupo.pissir.darts.locale.model;

import java.time.Instant;

/**
 * Riga della tabella edge_devices.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del dispositivo edge
 * @param dartboardId identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param serialNumber numero seriale del dispositivo
 * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
 * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
 * @param qos livello MQTT di qualità del servizio
 * @param urlBrokerLocale indirizzo del broker MQTT locale
 * @param ultimoHeartbeat istante dell'ultimo segnale di presenza
 * @param tiriInCoda numero di tiri in attesa di sincronizzazione
 * @param stato stato da impostare o verificare
 * @param deviceUserId identificativo dell'account tecnico del dispositivo
 * @see java.time.Instant
 *  
 */
public record EdgeDevice(long id, long dartboardId, long localeId, String serialNumber,
                          int intervalloHeartbeatS, int sogliaOfflineS, int qos, String urlBrokerLocale,
                          Instant ultimoHeartbeat, int tiriInCoda, EdgeDeviceStatus stato, Long deviceUserId) {

}
