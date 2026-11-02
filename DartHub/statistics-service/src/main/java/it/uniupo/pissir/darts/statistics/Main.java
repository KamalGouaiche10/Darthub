package it.uniupo.pissir.darts.statistics;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtConfigFactory;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.statistics.config.ServiceConfig;
import it.uniupo.pissir.darts.statistics.controller.GameStatisticsController;
import it.uniupo.pissir.darts.statistics.controller.GlobalStatisticsController;
import it.uniupo.pissir.darts.statistics.controller.LocaleStatisticsController;
import it.uniupo.pissir.darts.statistics.controller.PlayerStatisticsController;
import it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao;
import it.uniupo.pissir.darts.statistics.mqtt.LocaleCountSubscriber;
import it.uniupo.pissir.darts.statistics.mqtt.MatchCompletedSubscriber;
import it.uniupo.pissir.darts.statistics.mqtt.ThrowEventSubscriber;
import it.uniupo.pissir.darts.statistics.mqtt.TournamentUpdatedSubscriber;
import it.uniupo.pissir.darts.statistics.mqtt.UserCountSubscriber;
import it.uniupo.pissir.darts.statistics.service.StatisticsAggregationService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.time.Instant;

/**
 * Avvia il componente {@code statistics-service} e ne collega le dipendenze. 
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

        PlayerStatisticsDao playerStatisticsDao = new PlayerStatisticsDao(connectionProvider);
        TeamStatisticsDao teamStatisticsDao = new TeamStatisticsDao(connectionProvider);
        LocaleStatisticsDao localeStatisticsDao = new LocaleStatisticsDao(connectionProvider);
        GameStatisticsDao gameStatisticsDao = new GameStatisticsDao(connectionProvider);
        GlobalStatisticsDao globalStatisticsDao = new GlobalStatisticsDao(connectionProvider);
        PlayerVisibilityDao playerVisibilityDao = new PlayerVisibilityDao(connectionProvider);
        LocaleVisibilityDao localeVisibilityDao = new LocaleVisibilityDao(connectionProvider);
        ProcessedEventDao processedEventDao = new ProcessedEventDao();

        StatisticsAggregationService statisticsAggregationService = new StatisticsAggregationService(connectionProvider,
                playerStatisticsDao, teamStatisticsDao, localeStatisticsDao, gameStatisticsDao, globalStatisticsDao,
                playerVisibilityDao, localeVisibilityDao, processedEventDao);

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;

        new MatchCompletedSubscriber(eventBus, statisticsAggregationService).start();
        new TournamentUpdatedSubscriber(eventBus, statisticsAggregationService).start();
        new ThrowEventSubscriber(eventBus, statisticsAggregationService).start();
        new UserCountSubscriber(eventBus, statisticsAggregationService).start();
        new LocaleCountSubscriber(eventBus, statisticsAggregationService).start();

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        new PlayerStatisticsController(playerStatisticsDao, teamStatisticsDao, playerVisibilityDao, jwtTokenService).register();
        new LocaleStatisticsController(localeStatisticsDao, localeVisibilityDao, jwtTokenService).register();
        new GameStatisticsController(gameStatisticsDao, jwtTokenService).register();
        new GlobalStatisticsController(globalStatisticsDao, jwtTokenService).register();

        Instant startedAt = Instant.now();
        MqttClient healthMqttClient = mqttClient;
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            String mqttStatus = healthMqttClient != null && healthMqttClient.isConnected()
                    ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO;
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("statistics-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK, mqttStatus));
        });

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("statistics-service avviato sulla porta {}", config.port());
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
                    + "(GET /health riporterà mqtt=DISCONNESSO, le statistiche non si aggiorneranno)", config.mqttBrokerUrl(), e.getMessage());
            return null;
        }
    }
}
