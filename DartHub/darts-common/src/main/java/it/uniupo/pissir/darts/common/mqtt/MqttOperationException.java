package it.uniupo.pissir.darts.common.mqtt;

/**
 * Segnala una condizione di MQTT operation nelle operazioni della piattaforma. 
 */
public final class MqttOperationException extends RuntimeException {

    /**
     * Inizializza {@code MqttOperationException} con i dati e le dipendenze indicati.
     *
     * @param message messaggio da elaborare
     * @param cause causa originale dell'errore
     *  
     */
    public MqttOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
