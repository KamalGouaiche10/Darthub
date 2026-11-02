package it.uniupo.pissir.darts.game;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtConfigFactory;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.game.config.ServiceConfig;
import it.uniupo.pissir.darts.game.controller.GameTypeController;
import it.uniupo.pissir.darts.game.controller.ScoringRuleController;
import it.uniupo.pissir.darts.game.controller.SensorTypeController;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dao.ScoringRuleDao;
import it.uniupo.pissir.darts.game.dao.SensorTypeDao;
import it.uniupo.pissir.darts.game.service.GameTypeService;
import it.uniupo.pissir.darts.game.service.ScoringRuleService;
import it.uniupo.pissir.darts.game.service.SensorTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.time.Instant;

/**
 * Avvia il componente {@code game-service} e ne collega le dipendenze. 
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
        if (Boolean.getBoolean("darthub.seedDemo")) {
            SchemaInitializer.initialize(connectionProvider, "seed.sql");
        }

        JwtConfig jwtConfig = JwtConfigFactory.forVerifier(config.raw());
        JwtTokenService jwtTokenService = new JwtTokenService(jwtConfig);

        GameTypeDao gameTypeDao = new GameTypeDao(connectionProvider);
        SensorTypeDao sensorTypeDao = new SensorTypeDao(connectionProvider);
        ScoringRuleDao scoringRuleDao = new ScoringRuleDao(connectionProvider);

        GameTypeService gameTypeService = new GameTypeService(gameTypeDao);
        SensorTypeService sensorTypeService = new SensorTypeService(sensorTypeDao, gameTypeService);
        ScoringRuleService scoringRuleService = new ScoringRuleService(scoringRuleDao, gameTypeService);

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        new GameTypeController(gameTypeService, jwtTokenService).register();
        new SensorTypeController(sensorTypeService, jwtTokenService).register();
        new ScoringRuleController(scoringRuleService, jwtTokenService).register();

        Instant startedAt = Instant.now();
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("game-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK,
                    HealthDTO.MQTT_DISCONNESSO));
        });

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("game-service avviato sulla porta {}", config.port());
    }
}
