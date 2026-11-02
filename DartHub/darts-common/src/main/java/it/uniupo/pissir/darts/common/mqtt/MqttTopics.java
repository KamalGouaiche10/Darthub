package it.uniupo.pissir.darts.common.mqtt;

/**
 * Costruisce i topic MQTT condivisi fra sensori, edge e servizi. 
 */
public final class MqttTopics {

    public static final String EVENTI_PARTITE_EVENTO = "eventi/partite/evento";
    public static final String EVENTI_PARTITE_COMPLETATA = "eventi/partite/completata";
    public static final String EVENTI_TORNEI_AGGIORNATO = "eventi/tornei/aggiornato";
    public static final String EVENTI_UTENTI_CONTEGGIO = "eventi/utenti/conteggio-cambiato";
    public static final String EVENTI_LOCALI_CONTEGGIO = "eventi/locali/conteggio-cambiato";
    public static final String SISTEMA_PING = "sistema/ping";

    public static final String GIOCHI_EVENTO_WILDCARD = "giochi/+/+/evento";

    public static final String EDGE_HEARTBEAT_WILDCARD = "edge/+/+/heartbeat";

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private MqttTopics() {
    }

    /**
     * Costruisce il topic degli eventi del sensore.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String sensoreEvento(long localeId, long deviceId) {
        return "sensori/" + localeId + "/" + deviceId + "/evento";
    }

    /**
     * Costruisce il topic dello stato del sensore.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String sensoreStato(long localeId, long deviceId) {
        return "sensori/" + localeId + "/" + deviceId + "/stato";
    }

    /**
     * Costruisce il topic degli eventi di gioco inoltrati dall'edge.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String giochiEvento(long localeId, long deviceId) {
        return "giochi/" + localeId + "/" + deviceId + "/evento";
    }

    /**
     * Costruisce il topic dei comandi destinati all'edge.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String giochiComandi(long localeId, long deviceId) {
        return "giochi/" + localeId + "/" + deviceId + "/comandi";
    }

    /**
     * Costruisce il topic degli aggiornamenti del punteggio.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String giochiPunteggio(long localeId, long deviceId) {
        return "giochi/" + localeId + "/" + deviceId + "/punteggio";
    }

    /**
     * Costruisce il topic della configurazione del bersaglio.
     *
     * @param localeId identificativo del locale
     * @param deviceId identificativo del dispositivo
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String giochiConfig(long localeId, long deviceId) {
        return "giochi/" + localeId + "/" + deviceId + "/config";
    }

    /**
     * Costruisce il topic dei segnali di presenza dell'edge.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return il topic MQTT associato ai dati indicati
     *  
     */
    public static String edgeHeartbeat(long localeId, long dartboardId) {
        return "edge/" + localeId + "/" + dartboardId + "/heartbeat";
    }
}
