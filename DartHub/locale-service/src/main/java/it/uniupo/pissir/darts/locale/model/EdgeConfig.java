package it.uniupo.pissir.darts.locale.model;

/**
 * Configurazione di un dispositivo edge: corpo di PUT /edge-devices/{id/config} e payload pubblicato (retained) su darts/{localeId/{dartboardId}/config}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
 * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
 * @param qos livello MQTT di qualità del servizio
 * @param urlBrokerLocale indirizzo del broker MQTT locale
 *  
 */
public record EdgeConfig(int intervalloHeartbeatS, int sogliaOfflineS, int qos, String urlBrokerLocale) {
}
