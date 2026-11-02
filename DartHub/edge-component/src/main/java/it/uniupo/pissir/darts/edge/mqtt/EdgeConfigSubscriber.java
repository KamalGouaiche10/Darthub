package it.uniupo.pissir.darts.edge.mqtt;

import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.edge.config.EdgeProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Riceve ed elabora i messaggi MQTT relativi a dispositivi edge.
 *
 * @see it.uniupo.pissir.darts.edge.config.EdgeProperties
 *  
 */
public final class EdgeConfigSubscriber {
    /**
     * Raccoglie i dati necessari ad avviare il simulatore di un bersaglio.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
     * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
     * @param qos livello MQTT di qualità del servizio
     * @param urlBrokerLocale indirizzo del broker MQTT locale
     *  
     */
    public record Configuration(int intervalloHeartbeatS, int sogliaOfflineS, int qos, String urlBrokerLocale) {
        /**
         * Inizializza {@code Configuration} con i dati e le dipendenze indicati.
         *
         * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
         * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
         * @param qos livello MQTT di qualità del servizio
         * @param urlBrokerLocale indirizzo del broker MQTT locale
         * @throws IllegalArgumentException se viene rilevato l'errore «Parametri configurazione edge non validi»
         *  
         */
        public Configuration {
            if (intervalloHeartbeatS < 1 || sogliaOfflineS <= intervalloHeartbeatS || qos < 0 || qos > 2)
                throw new IllegalArgumentException("Parametri configurazione edge non validi");
            if (urlBrokerLocale != null && !urlBrokerLocale.isBlank()) {
                URI uri = URI.create(urlBrokerLocale);
                if (!("tcp".equals(uri.getScheme()) || "ssl".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IllegalArgumentException("URL broker non valido");
            }
        }
    }
    private final EdgeProperties properties;
    private final MqttEventBus central;
    private final HeartbeatPublisher heartbeat;
    private final CloudPublisher cloud;
    private final Function<MqttEventBus, LocalSensorSubscriber> sensors;
    private MqttClient localClient;
    private MqttEventBus localBus;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edge-configuration"); t.setDaemon(true); return t;
    });

    /**
     * Inizializza {@code EdgeConfigSubscriber} con i dati e le dipendenze indicati.
     *
     * @param properties proprietà di configurazione
     * @param central central da utilizzare
     * @param localClient componente {@link org.eclipse.paho.client.mqttv3.MqttClient} utilizzato dall'operazione
     * @param localBus bus MQTT locale
     * @param heartbeat segnale di presenza da utilizzare
     * @param cloud cloud da utilizzare
     * @param sensors sensors da utilizzare
     * @see it.uniupo.pissir.darts.edge.config.EdgeProperties
     *  
     */
    public EdgeConfigSubscriber(EdgeProperties properties, MqttEventBus central, MqttClient localClient,
            MqttEventBus localBus, HeartbeatPublisher heartbeat, CloudPublisher cloud,
            Function<MqttEventBus, LocalSensorSubscriber> sensors) {
        this.properties = properties; this.central = central; this.localClient = localClient;
        this.localBus = localBus; this.heartbeat = heartbeat; this.cloud = cloud; this.sensors = sensors;
    }
    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (central == null) return;
        central.subscribe(MqttTopics.giochiConfig(properties.localeId(), properties.dartboardId()), 1,
            Configuration.class, (topic, config) -> worker.execute(() -> {
                try { apply(config); }
                catch (Exception e) { LoggerFactory.getLogger(getClass()).warn("Configurazione edge non applicata: {}", e.getMessage()); }
            }));
    }
    /**
     * Applica i parametri ricevuti alla configurazione dell'edge.
     *
     * @param config configurazione del componente
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     * @see it.uniupo.pissir.darts.edge.mqtt.EdgeConfigSubscriber.Configuration
     *  
     */
    private void apply(Configuration config) throws Exception {
        String broker = config.urlBrokerLocale() == null || config.urlBrokerLocale().isBlank()
                ? properties.brokerLocale() : config.urlBrokerLocale();
        boolean replace = localClient == null || !broker.equals(properties.brokerLocale());
        MqttClient nextClient = localClient;
        MqttEventBus nextBus = localBus;
        try {
            if (replace) {
                nextClient = MqttConnectionFactory.connect(broker, "edge-" + properties.dartboardId() + "-locale-" + java.util.UUID.randomUUID(),
                        properties.mqttUsername(), properties.mqttPassword(), null);
                nextBus = new MqttEventBus(nextClient);
            }
            sensors.apply(nextBus).start(config.qos());
            properties.salvaConfigurazione(config.intervalloHeartbeatS(), config.sogliaOfflineS(), config.qos(), broker);
        } catch (Exception e) {
            if (replace) close(nextClient, nextBus);
            else sensors.apply(localBus).start(properties.mqttQos());
            throw e;
        }
        if (replace) close(localClient, localBus);
        localClient = nextClient; localBus = nextBus;
        cloud.aggiornaQos(config.qos());
        heartbeat.aggiornaIntervallo(config.intervalloHeartbeatS());
        LoggerFactory.getLogger(getClass()).info("Configurazione edge applicata: heartbeat={}s, qos={}", config.intervalloHeartbeatS(), config.qos());
    }
    /**
     * Chiude il componente e rilascia le risorse utilizzate.
     *
     * @param client client da utilizzare
     * @param bus bus dei messaggi
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    private void close(MqttClient client, MqttEventBus bus) {
        if (bus != null) bus.close();
        if (client != null) try { if (client.isConnected()) client.disconnect(); client.close(); }
        catch (Exception e) { LoggerFactory.getLogger(getClass()).warn("Chiusura precedente client locale: {}", e.getMessage()); }
    }
}
