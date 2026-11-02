package it.uniupo.pissir.darts.tournament;

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
import it.uniupo.pissir.darts.tournament.config.ServiceConfig;
import it.uniupo.pissir.darts.tournament.controller.StandingsController;
import it.uniupo.pissir.darts.tournament.controller.TournamentController;
import it.uniupo.pissir.darts.tournament.controller.MatchAppointmentController;
import it.uniupo.pissir.darts.tournament.dao.BracketSlotDao;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.tournament.dao.PairingNoticeDao;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao;
import it.uniupo.pissir.darts.tournament.mqtt.MatchCompletedSubscriber;
import it.uniupo.pissir.darts.tournament.mqtt.TournamentEventPublisher;
import it.uniupo.pissir.darts.tournament.service.EnrollmentService;
import it.uniupo.pissir.darts.tournament.service.GameTypeClient;
import it.uniupo.pissir.darts.tournament.service.LocaleClient;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.MatchAppointmentService;
import it.uniupo.pissir.darts.tournament.service.PairingService;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.StandingsService;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.service.TournamentLifecycleScheduler;
import it.uniupo.pissir.darts.tournament.service.UserClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.time.Instant;

/**
 * Avvia il componente {@code tournament-service} e ne collega le dipendenze. 
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

        TournamentDao tournamentDao = new TournamentDao(connectionProvider);
        TournamentLocaleDao tournamentLocaleDao = new TournamentLocaleDao(connectionProvider);
        TournamentMatchDao tournamentMatchDao = new TournamentMatchDao(connectionProvider);
        MatchAppointmentDao appointmentDao = new MatchAppointmentDao(connectionProvider);
        BracketSlotDao bracketSlotDao = new BracketSlotDao();
        EnrollmentDao enrollmentDao = new EnrollmentDao(connectionProvider);
        StandingDao standingDao = new StandingDao(connectionProvider);
        ProcessedEventDao processedEventDao = new ProcessedEventDao();

        ServiceTokenClient serviceTokenClient = new ServiceTokenClient(httpClient, config.userServiceBaseUrl(),
                "tournament-service", config.internalServiceSecret());

        GameTypeClient gameTypeClient = new GameTypeClient(httpClient, config.gameServiceBaseUrl(), serviceTokenClient);
        LocaleClient localeClient = new LocaleClient(httpClient, config.localeServiceBaseUrl(), serviceTokenClient);
        UserClient userClient = new UserClient(httpClient, config.userServiceBaseUrl(), serviceTokenClient);
        MatchServiceClient matchServiceClient = new MatchServiceClient(httpClient, config.matchServiceBaseUrl(),
                config.gameServiceBaseUrl(), serviceTokenClient);

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;
        TournamentEventPublisher tournamentEventPublisher = new TournamentEventPublisher(eventBus);

        TournamentService tournamentService = new TournamentService(tournamentDao, tournamentLocaleDao, tournamentMatchDao,
                standingDao, gameTypeClient, localeClient, matchServiceClient);
        EnrollmentService enrollmentService = new EnrollmentService(enrollmentDao, tournamentService, userClient,
                matchServiceClient);
        ScheduleGenerator scheduleGenerator = new ScheduleGenerator(connectionProvider, tournamentLocaleDao,
                tournamentMatchDao, bracketSlotDao, enrollmentDao, localeClient, matchServiceClient, tournamentService);
        MatchAppointmentService appointmentService = new MatchAppointmentService(appointmentDao, tournamentMatchDao,
                tournamentService, matchServiceClient, java.time.Clock.systemUTC(), 5);
        java.util.concurrent.ScheduledExecutorService calendarWorker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "tournament-calendar");
            thread.setDaemon(true);
            return thread;
        });
        StandingsService standingsService = new StandingsService(connectionProvider, tournamentDao, tournamentMatchDao,
                standingDao, processedEventDao, scheduleGenerator, tournamentEventPublisher);
        appointmentService.setStandingsService(standingsService);
        calendarWorker.scheduleWithFixedDelay(new TournamentLifecycleScheduler(tournamentDao, scheduleGenerator,
                tournamentService), 0, 5, java.util.concurrent.TimeUnit.SECONDS);
        calendarWorker.scheduleWithFixedDelay(appointmentService::processDue, 1, 5, java.util.concurrent.TimeUnit.SECONDS);

        new MatchCompletedSubscriber(eventBus, standingsService).start();

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        PairingService pairingService = new PairingService(tournamentDao, tournamentMatchDao, enrollmentDao,
                new PairingNoticeDao(connectionProvider), userClient, matchServiceClient, localeClient, appointmentService);
        new TournamentController(tournamentService, enrollmentService, scheduleGenerator, pairingService,
                jwtTokenService).register();
        new MatchAppointmentController(appointmentService, jwtTokenService).register();
        new StandingsController(standingDao, tournamentService, userClient, matchServiceClient,
                jwtTokenService).register();

        Instant startedAt = Instant.now();
        MqttClient healthMqttClient = mqttClient;
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            String mqttStatus = healthMqttClient != null && healthMqttClient.isConnected()
                    ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO;
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("tournament-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK, mqttStatus));
        });

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("tournament-service avviato sulla porta {}", config.port());
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
                    + "(GET /health riporterà mqtt=DISCONNESSO, la classifica non si aggiornerà)", config.mqttBrokerUrl(), e.getMessage());
            return null;
        }
    }
}
