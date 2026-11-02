package it.uniupo.pissir.darts.match;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtConfigFactory;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.match.config.ServiceConfig;
import it.uniupo.pissir.darts.match.controller.MatchController;
import it.uniupo.pissir.darts.match.controller.OfflineSyncController;
import it.uniupo.pissir.darts.match.controller.ParticipantController;
import it.uniupo.pissir.darts.match.controller.RosterController;
import it.uniupo.pissir.darts.match.controller.TeamController;
import it.uniupo.pissir.darts.match.controller.ThrowController;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.dao.TeamDao;
import it.uniupo.pissir.darts.match.dao.TeamMemberDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;
import it.uniupo.pissir.darts.match.mqtt.ThrowEventSubscriber;
import it.uniupo.pissir.darts.match.service.DartboardStatusClient;
import it.uniupo.pissir.darts.match.service.GameTypeClient;
import it.uniupo.pissir.darts.match.service.MatchLockRegistry;
import it.uniupo.pissir.darts.match.service.MatchResultService;
import it.uniupo.pissir.darts.match.service.MatchService;
import it.uniupo.pissir.darts.match.service.OfflineSyncService;
import it.uniupo.pissir.darts.match.service.ParticipantService;
import it.uniupo.pissir.darts.match.service.PlayerNameResolver;
import it.uniupo.pissir.darts.match.service.ScoringRuleClient;
import it.uniupo.pissir.darts.match.service.TeamNameResolver;
import it.uniupo.pissir.darts.match.service.TeamService;
import it.uniupo.pissir.darts.match.service.ThrowIngestionService;
import it.uniupo.pissir.darts.match.service.TournamentAccessClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.time.Instant;

/**
 * Avvia il componente {@code match-service} e ne collega le dipendenze. 
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

        MatchDao matchDao = new MatchDao(connectionProvider);
        MatchParticipantDao participantDao = new MatchParticipantDao(connectionProvider);
        ThrowDao throwDao = new ThrowDao(connectionProvider);
        TeamDao teamDao = new TeamDao(connectionProvider);
        TeamMemberDao teamMemberDao = new TeamMemberDao(connectionProvider);
        MatchResultDao matchResultDao = new MatchResultDao(connectionProvider);

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;

        ServiceTokenClient serviceTokenClient = new ServiceTokenClient(httpClient, config.userServiceBaseUrl(),
                "match-service", config.internalServiceSecret());

        ScoringRuleClient scoringRuleClient = new ScoringRuleClient(httpClient, config.gameServiceBaseUrl(), serviceTokenClient);
        GameTypeClient gameTypeClient = new GameTypeClient(httpClient, config.gameServiceBaseUrl(), serviceTokenClient);
        DartboardStatusClient dartboardStatusClient = new DartboardStatusClient(httpClient, config.localeServiceBaseUrl(),
                serviceTokenClient);
        PlayerNameResolver playerNameResolver = new PlayerNameResolver(httpClient, config.userServiceBaseUrl(), serviceTokenClient);
        TeamNameResolver teamNameResolver = new TeamNameResolver(teamDao);

        MatchEventPublisher matchEventPublisher = new MatchEventPublisher(eventBus);
        EdgeCommandPublisher edgeCommandPublisher = new EdgeCommandPublisher(eventBus);
        MatchLockRegistry matchLockRegistry = new MatchLockRegistry();

        MatchResultService matchResultService = new MatchResultService(matchResultDao, matchDao, dartboardStatusClient,
                edgeCommandPublisher, matchEventPublisher);
        MatchService matchService = new MatchService(connectionProvider, matchDao, participantDao, throwDao,
                teamNameResolver, scoringRuleClient, dartboardStatusClient, edgeCommandPublisher, matchResultService,
                playerNameResolver);
        ParticipantService participantService = new ParticipantService(connectionProvider, participantDao, matchService);
        TeamService teamService = new TeamService(teamDao, teamMemberDao);
        ThrowIngestionService throwIngestionService = new ThrowIngestionService(connectionProvider, matchDao,
                participantDao, throwDao, scoringRuleClient, gameTypeClient, matchResultService, matchEventPublisher,
                edgeCommandPublisher, matchLockRegistry, teamNameResolver, playerNameResolver);
        OfflineSyncService offlineSyncService = new OfflineSyncService(connectionProvider, matchDao, participantDao,
                throwDao, matchResultDao, scoringRuleClient, matchLockRegistry, matchResultService, dartboardStatusClient);

        new ThrowEventSubscriber(eventBus, throwIngestionService).start();

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        new MatchController(matchService, jwtTokenService).register();
        new ParticipantController(participantService, matchService, jwtTokenService).register();
        new ThrowController(matchService, throwIngestionService, jwtTokenService,
                new TournamentAccessClient(httpClient, config.tournamentServiceBaseUrl())).register();
        new RosterController(matchService, jwtTokenService).register();
        new OfflineSyncController(offlineSyncService, jwtTokenService).register();
        new TeamController(teamService, jwtTokenService).register();

        Instant startedAt = Instant.now();
        MqttClient healthMqttClient = mqttClient;
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            String mqttStatus = healthMqttClient != null && healthMqttClient.isConnected()
                    ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO;
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("match-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK, mqttStatus));
        });

        Spark.init();
        Spark.awaitInitialization();
        matchService.ripubblicaPartiteAttive();
        LOG.info("match-service avviato sulla porta {}", config.port());
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
                    + "(GET /health riporterà mqtt=DISCONNESSO, nessun tiro live via MQTT)", config.mqttBrokerUrl(), e.getMessage());
            return null;
        }
    }
}
