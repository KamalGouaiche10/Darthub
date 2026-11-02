package it.uniupo.pissir.darts.common.dto;

/**
 * Payload di GET /health, esposto senza autenticazione da ogni servizio.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param servizio nome del servizio che espone lo stato
 * @param versione versione del componente
 * @param uptimeS tempo di attivitÃ  del servizio in secondi
 * @param database stato del collegamento al database
 * @param mqtt stato del collegamento MQTT
 *  
 */
public record HealthDTO(String servizio, String versione, long uptimeS, String database, String mqtt) {

    public static final String DATABASE_OK = "OK";
    public static final String MQTT_CONNESSO = "CONNESSO";
    public static final String MQTT_DISCONNESSO = "DISCONNESSO";
}
