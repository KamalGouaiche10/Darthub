package it.uniupo.pissir.darts.locale;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtConfigFactory;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.locale.config.ServiceConfig;
import it.uniupo.pissir.darts.locale.controller.DartboardController;
import it.uniupo.pissir.darts.locale.controller.EdgeDeviceController;
import it.uniupo.pissir.darts.locale.controller.LocaleController;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.mqtt.EdgeConfigPublisher;
import it.uniupo.pissir.darts.locale.mqtt.EdgeStatusMonitor;
import it.uniupo.pissir.darts.locale.mqtt.HeartbeatSubscriber;
import it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher;
import it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService;
import it.uniupo.pissir.darts.locale.service.DartboardService;
import it.uniupo.pissir.darts.locale.service.EdgeDeviceService;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.time.Instant;
import java.util.List;

/**
 * Avvia il componente {@code locale-service} e ne collega le dipendenze. 
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String VERSION = "1.0.0-SNAPSHOT";

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private Main() {
    }

    /**
     * Avvia il componente e ne inizializza le risorse.
     *
     * @param args argomenti della riga di comando
     *  
     */
    public static void main(String[] args) {
        ServiceConfig config = ServiceConfig.load();

        File dbFile = new File(config.databasePath());
        File dbDir = dbFile.getParentFile();
        if (dbDir != null) {
            dbDir.mkdirs();
        }

        ConnectionProvider connectionProvider = new ConnectionProvider(config.databasePath(), config.databasePoolSize());
        SchemaInitializer.initialize(connectionProvider, "schema.sql");
        MigrationRunner.run(connectionProvider, "migrations");

        JwtConfig jwtConfig = JwtConfigFactory.forVerifier(config.raw());
        JwtTokenService jwtTokenService = new JwtTokenService(jwtConfig);
        ServiceHttpClient httpClient = new ServiceHttpClient(config.httpTimeout());

        LocaleDao localeDao = new LocaleDao(connectionProvider);
        DartboardDao dartboardDao = new DartboardDao(connectionProvider);
        EdgeDeviceDao edgeDeviceDao = new EdgeDeviceDao(connectionProvider);

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;

        LocaleEventPublisher localeEventPublisher = new LocaleEventPublisher(eventBus);
        backfillConteggioLocali(localeDao, localeEventPublisher);

        LocaleService localeService = new LocaleService(localeDao, localeEventPublisher);
        DartboardService dartboardService = new DartboardService(dartboardDao, localeService, edgeDeviceDao);
        EdgeConfigPublisher edgeConfigPublisher = new EdgeConfigPublisher(eventBus);
        EdgeDeviceService edgeDeviceService = new EdgeDeviceService(connectionProvider, edgeDeviceDao, dartboardService,
                edgeConfigPublisher, httpClient, config.userServiceBaseUrl());
        ServiceTokenClient serviceTokenClient = new ServiceTokenClient(httpClient, config.userServiceBaseUrl(),
                "locale-service", config.internalServiceSecret());
        ActiveMatchQueryService activeMatchQueryService = new ActiveMatchQueryService(httpClient,
                config.matchServiceBaseUrl(), serviceTokenClient);

        new HeartbeatSubscriber(eventBus, connectionProvider, edgeDeviceDao, dartboardDao).start();
        EdgeStatusMonitor edgeStatusMonitor = new EdgeStatusMonitor(connectionProvider, edgeDeviceDao, dartboardDao);
        edgeStatusMonitor.start(config.edgeStatusMonitorIntervalSeconds());

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        new LocaleController(localeService, activeMatchQueryService, jwtTokenService).register();
        new DartboardController(dartboardService, localeService, jwtTokenService, activeMatchQueryService).register();
        new EdgeDeviceController(edgeDeviceService, dartboardService, localeService, jwtTokenService,
                activeMatchQueryService).register();

        Instant startedAt = Instant.now();
        MqttClient healthMqttClient = mqttClient;
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            String mqttStatus = healthMqttClient != null && healthMqttClient.isConnected()
                    ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO;
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("locale-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK, mqttStatus));
        });

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("locale-service avviato sulla porta {}", config.port());
    }

    /**
     * Prepara e apre il collegamento al broker MQTT.
     *
     * @param config configurazione del componente
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttClient} prodotto o recuperato
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    private static MqttClient connectMqtt(ServiceConfig config) {
        try {
            return MqttConnectionFactory.connect(config.mqttBrokerUrl(), config.mqttClientId(),
                    config.mqttUsername(), config.mqttPassword(), null);
        } catch (MqttException e) {
            LOG.warn("Impossibile connettersi al broker MQTT {}: {} — servizio avviato in modalità degradata "
                    + "(GET /health riporterà mqtt=DISCONNESSO)", config.mqttBrokerUrl(), e.getMessage());
            return null;
        }
    }

    /**
     * Pubblica il conteggio dei locali già presenti per riallineare le statistiche.
     *
     * @param localeDao componente {@link it.uniupo.pissir.darts.locale.dao.LocaleDao} utilizzato dall'operazione
     * @param localeEventPublisher componente {@link it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.dao.LocaleDao
     *  
     */
    private static void backfillConteggioLocali(LocaleDao localeDao, LocaleEventPublisher localeEventPublisher) {
        int pagina = 0;
        List<Locale> lotto;
        do {
            lotto = localeDao.search(null, null, Page.of(pagina, 100));
            for (Locale locale : lotto) {
                if (locale.contaAiFiniStatistiche()) {
                    localeEventPublisher.publishBackfill(locale.id());
                }
            }
            pagina++;
        } while (!lotto.isEmpty());
    }
}
