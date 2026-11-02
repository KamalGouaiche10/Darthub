package it.uniupo.pissir.darts.common.mqtt;

import com.google.gson.Gson;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Pubblica messaggi JSON e gestisce le sottoscrizioni MQTT. 
 */
public final class MqttEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(MqttEventBus.class);

    private final IMqttClient client;
    private final Gson gson;
    private final ExecutorService publishExecutor;
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService subscriptionRecovery;

    /**
     * Raccoglie i dati di subscription.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param qos livello MQTT di qualità del servizio
     * @param listener ascoltatore da registrare
     * @see org.eclipse.paho.client.mqttv3.IMqttMessageListener
     *  
     */
    private record Subscription(int qos, IMqttMessageListener listener) {
    }

    /**
     * Inizializza {@code MqttEventBus} con i dati e le dipendenze indicati.
     *
     * @param client client da utilizzare
     * @see org.eclipse.paho.client.mqttv3.IMqttClient
     *  
     */
    public MqttEventBus(IMqttClient client) {
        this(client, GsonFactory.instance());
    }

    /**
     * Inizializza {@code MqttEventBus} con i dati e le dipendenze indicati.
     *
     * @param client client da utilizzare
     * @param gson convertitore JSON
     * @see org.eclipse.paho.client.mqttv3.IMqttClient
     *  
     */
    public MqttEventBus(IMqttClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
        this.publishExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mqtt-publish");
            thread.setDaemon(true);
            return thread;
        });
        this.subscriptionRecovery = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mqtt-subscription-recovery");
            thread.setDaemon(true);
            return thread;
        });
        this.subscriptionRecovery.scheduleWithFixedDelay(this::restoreSubscriptions, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @param event evento da elaborare
     * @param qos livello MQTT di qualità del servizio
     *  
     */
    public void publish(String topic, Object event, int qos) {
        publish(topic, event, qos, false);
    }

    /**
     * Pubblica il contenuto sul topic MQTT indicato.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @param event evento da elaborare
     * @param qos livello MQTT di qualità del servizio
     * @param retained indicatore di conservazione del messaggio sul broker
     *  
     */
    public void publish(String topic, Object event, int qos, boolean retained) {
        String payload = gson.toJson(event);
        publishExecutor.execute(() -> {
            try {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                message.setRetained(retained);
                client.publish(topic, message);
            } catch (MqttException e) {
                LOG.warn("Pubblicazione MQTT fallita su {}: {}", topic, e.getMessage());
            } catch (RuntimeException e) {
                LOG.error("Pubblicazione MQTT fallita inaspettatamente su {}: {}", topic, e.getMessage(), e);
            }
        });
    }

    /**
     * Pubblica il messaggio segnalando gli errori di consegna.
     *
     * @param topic topic MQTT da pubblicare o sottoscrivere
     * @param event evento da elaborare
     * @param qos livello MQTT di qualità del servizio
     * @param retained indicatore di conservazione del messaggio sul broker
     *  
     */
    public void publishOrThrow(String topic, Object event, int qos, boolean retained) {
        String payload = gson.toJson(event);
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            client.publish(topic, message);
        } catch (MqttException e) {
            LOG.warn("Pubblicazione MQTT (sincrona) fallita su {}: {}", topic, e.getMessage());
            throw new MqttOperationException("Pubblicazione MQTT fallita su " + topic, e);
        }
    }

    /**
     * Registra il gestore dei messaggi per il filtro MQTT.
     *
     * @param <T> tipo degli elementi gestiti
     * @param topicFilter filtro dei topic MQTT da sottoscrivere
     * @param qos livello MQTT di qualità del servizio
     * @param type tipo da selezionare
     * @param handler gestore della richiesta o dell'evento
     * @see java.util.function.BiConsumer
     *  
     */
    public <T> void subscribe(String topicFilter, int qos, Class<T> type, BiConsumer<String, T> handler) {
        IMqttMessageListener listener = (topic, message) -> {
            try {
                T event = gson.fromJson(new String(message.getPayload(), StandardCharsets.UTF_8), type);
                handler.accept(topic, event);
            } catch (RuntimeException e) {
                LOG.error("Gestore del messaggio fallito su {}: {}", topic, e.getMessage(), e);
            }
        };
        try {
            client.subscribe(topicFilter, qos, listener);
            subscriptions.put(topicFilter, new Subscription(qos, listener));
        } catch (MqttException e) {
            throw new MqttOperationException("Sottoscrizione MQTT fallita su " + topicFilter, e);
        }
    }

    /**
     * Chiude il componente e rilascia le risorse utilizzate. 
     */
    public void close() {
        subscriptionRecovery.shutdownNow();
        publishExecutor.shutdown();
    }

    /**
     * Ripristina le sottoscrizioni dopo la riconnessione. 
     */
    private void restoreSubscriptions() {
        if (!client.isConnected()) {
            return;
        }
        subscriptions.forEach((topic, subscription) -> {
            try {
                client.subscribe(topic, subscription.qos(), subscription.listener());
            } catch (MqttException e) {
                LOG.warn("Ripristino della sottoscrizione MQTT {} fallito: {}", topic, e.getMessage());
            } catch (RuntimeException e) {
                LOG.error("Ripristino della sottoscrizione MQTT {} fallito", topic, e);
            }
        });
    }
}
