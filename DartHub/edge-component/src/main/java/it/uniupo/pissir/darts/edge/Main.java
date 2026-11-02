package it.uniupo.pissir.darts.edge;

import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.edge.bootstrap.EdgeBootstrap;
import it.uniupo.pissir.darts.edge.config.EdgeProperties;
import it.uniupo.pissir.darts.edge.dao.BufferedThrowDao;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.mqtt.CloudCommandSubscriber;
import it.uniupo.pissir.darts.edge.mqtt.CloudPublisher;
import it.uniupo.pissir.darts.edge.mqtt.HeartbeatPublisher;
import it.uniupo.pissir.darts.edge.mqtt.LocalSensorSubscriber;
import it.uniupo.pissir.darts.edge.service.ConnectionMonitor;
import it.uniupo.pissir.darts.edge.service.LocalMatchService;
import it.uniupo.pissir.darts.edge.service.MatchSessionManager;
import it.uniupo.pissir.darts.edge.service.OfflineStorageService;
import it.uniupo.pissir.darts.edge.service.RuleCache;
import it.uniupo.pissir.darts.edge.service.ScoreCalculatorService;
import it.uniupo.pissir.darts.edge.service.SyncManager;
import it.uniupo.pissir.darts.edge.web.LocalDisplayServer;
import it.uniupo.pissir.darts.edge.web.LocalScoreController;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Console;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Avvia il componente {@code edge-component} e ne collega le dipendenze. 
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private Main() {
    }

    /**
     * Avvia il componente e ne inizializza le risorse.
     *
     * @param args argomenti della riga di comando
     * @throws IllegalStateException se viene rilevato l'errore «Per inserire la password avvia la registrazione da un terminale interattivo»
     * @throws IllegalArgumentException se viene rilevato l'errore «Password non inserita»
     *  
     */
    public static void main(String[] args) {
        EdgeProperties properties = EdgeProperties.load();

        if (args.length > 0 && "--register".equals(args[0])) {
            Map<String, String> opzioni = parseArgs(args);
            String username = opzioni.get("username");
            String password = opzioni.get("password");
            String serialNumber = opzioni.getOrDefault("serialNumber", safeSerialNumber(properties));
            if (username == null) {
                LOG.error("Uso: --register --username <locale-admin> [--serialNumber <seriale>]");
                return;
            }
            if (password == null) {
                Console console = System.console();
                if (console == null) {
                    throw new IllegalStateException("Per inserire la password avvia la registrazione da un terminale interattivo");
                }
                char[] secret = console.readPassword("Password del gestore: ");
                if (secret == null || secret.length == 0) {
                    throw new IllegalArgumentException("Password non inserita");
                }
                try {
                    password = new String(secret);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
            EdgeBootstrap.register(properties, properties.localeServiceUrl(), properties.userServiceUrl(),
                    username, password, serialNumber);
            return;
        }

        long localeId = properties.localeId();
        long dartboardId = properties.dartboardId();

        File dbFile = new File(properties.pathDatabase());
        File dbDir = dbFile.getParentFile();
        if (dbDir != null) {
            dbDir.mkdirs();
        }

        ConnectionProvider connectionProvider = new ConnectionProvider(properties.pathDatabase(), properties.databasePoolSize());
        SchemaInitializer.initialize(connectionProvider, "schema.sql");
        MigrationRunner.run(connectionProvider, "migrations");

        LocalMatchDao localMatchDao = new LocalMatchDao(connectionProvider);
        BufferedThrowDao bufferedThrowDao = new BufferedThrowDao(connectionProvider);

        RuleCache ruleCache = new RuleCache(connectionProvider);
        ConnectionMonitor connectionMonitor = new ConnectionMonitor();
        ScoreCalculatorService scoreCalculatorService = new ScoreCalculatorService();
        OfflineStorageService offlineStorageService = new OfflineStorageService(bufferedThrowDao);
        MatchSessionManager matchSessionManager = new MatchSessionManager(connectionProvider, localMatchDao, bufferedThrowDao);
        LocalMatchService localMatchService = new LocalMatchService(connectionProvider, localMatchDao, ruleCache,
                matchSessionManager, dartboardId);
        SyncManager syncManager = new SyncManager(localMatchDao, bufferedThrowDao, properties, connectionMonitor);

        MqttClient clientLocale = connect(properties.brokerLocale(), "edge-" + dartboardId + "-locale",
                properties.mqttUsername(), properties.mqttPassword(), null, null, null, "broker locale");

        MqttCallbackExtended callbackCentrale = MqttConnectionFactory.callback(connectionMonitor::onConnectComplete,
                connectionMonitor::onConnectionLost);
        MqttClient clientCentrale = connect(properties.brokerCentrale(), "edge-" + dartboardId + "-centrale",
                properties.mqttUsername(), properties.mqttPassword(), callbackCentrale,
                MqttTopics.edgeHeartbeat(localeId, dartboardId), "{\"stato\":\"OFFLINE\"}", "broker centrale");

        MqttEventBus eventBusLocale = clientLocale != null ? new MqttEventBus(clientLocale) : null;
        MqttEventBus eventBusCentrale = clientCentrale != null ? new MqttEventBus(clientCentrale) : null;

        CloudPublisher cloudPublisher = new CloudPublisher(eventBusCentrale, localeId, dartboardId);
        cloudPublisher.aggiornaQos(properties.mqttQos());
        java.util.function.Function<MqttEventBus, LocalSensorSubscriber> sensors = bus ->
                new LocalSensorSubscriber(bus, localeId, dartboardId, scoreCalculatorService, matchSessionManager,
                        localMatchDao, cloudPublisher, connectionMonitor);
        sensors.apply(eventBusLocale).start(properties.mqttQos());
        new CloudCommandSubscriber(eventBusCentrale, localeId, dartboardId, localMatchDao, ruleCache, matchSessionManager)
                .start();
        HeartbeatPublisher heartbeat = new HeartbeatPublisher(eventBusCentrale, localeId, dartboardId, offlineStorageService, properties.intervalloHeartbeatSecondi());
        heartbeat.start();
        new it.uniupo.pissir.darts.edge.mqtt.EdgeConfigSubscriber(properties, eventBusCentrale, clientLocale,
                eventBusLocale, heartbeat, cloudPublisher, sensors).start();
        syncManager.start();

        LocalScoreController scoreController = new LocalScoreController(matchSessionManager, localMatchService,
                properties.displayAdminToken(), () -> java.util.Map.of(
                    "intervalloHeartbeatS", properties.intervalloHeartbeatSecondi(),
                    "qos", properties.mqttQos(), "urlBrokerLocale", properties.brokerLocale()));
        LocalDisplayServer displayServer = new LocalDisplayServer(scoreController, properties.portaDisplay());
        displayServer.start();

        LOG.info("edge-component avviato: locale={}, bersaglio={}, monitor su porta {}", localeId, dartboardId,
                properties.portaDisplay());
    }

    /**
     * Legge e converte args dalla richiesta o dalla configurazione.
     *
     * @param args argomenti della riga di comando
     * @return la mappa dei dati elaborati
     * @see java.util.Map
     *  
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opzioni = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                opzioni.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return opzioni;
    }

    /**
     * Ricava un numero seriale utilizzabile dal dispositivo.
     *
     * @param properties proprietà di configurazione
     * @return il testo o il valore di configurazione richiesto
     * @see it.uniupo.pissir.darts.edge.config.EdgeProperties
     *  
     */
    private static String safeSerialNumber(EdgeProperties properties) {
        try {
            return properties.serialNumber();
        } catch (RuntimeException e) {
            return "EDGE-" + properties.dartboardId();
        }
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
     * @param descrizione descrizione della risorsa
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttClient} prodotto o recuperato
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    private static MqttClient connect(String brokerUrl, String clientId, String username, char[] password,
                                      MqttCallbackExtended callback,
                                       String lastWillTopic, String lastWillPayload, String descrizione) {
        try {
            return MqttConnectionFactory.connect(brokerUrl, clientId, username, password, callback,
                    lastWillTopic, lastWillPayload);
        } catch (MqttException e) {
            LOG.warn("Impossibile connettersi al {} ({}): {} — avvio in modalità degradata", descrizione, brokerUrl,
                    e.getMessage());
            return null;
        }
    }
}
