package it.uniupo.pissir.darts.common.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Crea le connessioni MQTT con le opzioni configurate. 
 */
public final class MqttConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MqttConnectionFactory.class);
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private MqttConnectionFactory() {
    }

    /**
     * Apre la connessione MQTT con le opzioni indicate.
     *
     * @param brokerUrl indirizzo del broker MQTT
     * @param clientId identificativo del client MQTT
     * @param username nome dell'account
     * @param password password da verificare o registrare
     * @param callback funzione da richiamare al verificarsi dell'evento
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttClient} prodotto o recuperato
     * @throws MqttException se l'operazione non può essere completata
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    public static MqttClient connect(String brokerUrl, String clientId, String username, char[] password,
                                      MqttCallbackExtended callback) throws MqttException {
        return connect(brokerUrl, clientId, username, password, callback, null, null);
    }

    /**
     * Apre la connessione MQTT con le opzioni indicate.
     *
     * @param brokerUrl indirizzo del broker MQTT
     * @param clientId identificativo del client MQTT
     * @param username nome dell'account
     * @param password password da verificare o registrare
     * @param callback funzione da richiamare al verificarsi dell'evento
     * @param lastWillTopic topic del messaggio di disconnessione inattesa
     * @param lastWillPayload contenuto del messaggio di disconnessione inattesa
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttClient} prodotto o recuperato
     * @throws MqttException se l'operazione non può essere completata
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    public static MqttClient connect(String brokerUrl, String clientId, String username, char[] password,
                                      MqttCallbackExtended callback, String lastWillTopic, String lastWillPayload)
            throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        if (username != null) {
            options.setUserName(username);
            options.setPassword(password);
        }
        if (lastWillTopic != null && lastWillPayload != null) {
            options.setWill(lastWillTopic, lastWillPayload.getBytes(StandardCharsets.UTF_8), 0, false);
        }
        if (callback != null) {
            client.setCallback(callback);
        }
        client.connect(options);
        return client;
    }

    /**
     * Prepara il gestore degli eventi di connessione e dei messaggi MQTT.
     *
     * @param onConnectComplete azione da eseguire alla connessione
     * @param onConnectionLost azione da eseguire alla perdita della connessione
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttCallbackExtended} prodotto o recuperato
     * @see org.eclipse.paho.client.mqttv3.MqttCallbackExtended
     *  
     */
    public static MqttCallbackExtended callback(Consumer<Boolean> onConnectComplete, Consumer<Throwable> onConnectionLost) {
        return new MqttCallbackExtended() {

            /**
             * Ripristina lo stato del componente dopo la connessione MQTT.
             *
             * @param reconnect indicatore di riconnessione del client
             * @param serverUri indirizzo del server MQTT
             *  
             */
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                try {
                    onConnectComplete.accept(reconnect);
                } catch (RuntimeException e) {
                    LOG.error("Errore nell'hook connectComplete", e);
                }
            }

            /**
             * Gestisce la perdita della connessione MQTT.
             *
             * @param cause causa originale dell'errore
             *  
             */
            @Override
            public void connectionLost(Throwable cause) {
                try {
                    onConnectionLost.accept(cause);
                } catch (RuntimeException e) {
                    LOG.error("Errore nell'hook connectionLost", e);
                }
            }

            /**
             * Riceve e inoltra il messaggio MQTT al gestore associato.
             *
             * @param topic topic MQTT da pubblicare o sottoscrivere
             * @param message messaggio da elaborare
             * @see org.eclipse.paho.client.mqttv3.MqttMessage
             *  
             */
            @Override
            public void messageArrived(String topic, MqttMessage message) {

            }

            /**
             * Gestisce la conferma di consegna del messaggio MQTT.
             *
             * @param token token da utilizzare
             * @see org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
             *  
             */
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        };
    }
}
