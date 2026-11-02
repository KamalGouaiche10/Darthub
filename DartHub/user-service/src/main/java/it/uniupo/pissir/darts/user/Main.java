package it.uniupo.pissir.darts.user;

import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtSigningKeyStore;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.user.config.ServiceConfig;
import it.uniupo.pissir.darts.user.controller.AuthController;
import it.uniupo.pissir.darts.user.controller.UserController;
import it.uniupo.pissir.darts.user.dao.PlayerProfileDao;
import it.uniupo.pissir.darts.user.dao.RefreshTokenDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.mqtt.UserEventPublisher;
import it.uniupo.pissir.darts.user.service.AuthService;
import it.uniupo.pissir.darts.user.service.DeviceTokenService;
import it.uniupo.pissir.darts.user.service.DemoAdminBootstrap;
import it.uniupo.pissir.darts.user.service.PasswordHasher;
import it.uniupo.pissir.darts.user.service.UserService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Avvia il componente {@code user-service} e ne collega le dipendenze. 
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
     * @throws IllegalArgumentException se viene rilevato l'errore «Uso: --bootstrap-admin &lt;username&gt; &lt;email&gt;»
     * @throws IllegalStateException se viene rilevato l'errore «Eseguire il bootstrap da un terminale interattivo»
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

        if (args.length > 0 && "--bootstrap-admin".equals(args[0])) {
            if (args.length != 3) {
                throw new IllegalArgumentException("Uso: --bootstrap-admin <username> <email>");
            }
            Console console = System.console();
            if (console == null) {
                throw new IllegalStateException("Eseguire il bootstrap da un terminale interattivo");
            }
            char[] password = console.readPassword("Password del primo amministratore: ");
            if (password == null) {
                throw new IllegalStateException("Password non inserita");
            }
            try {
                long id = DemoAdminBootstrap.create(connectionProvider, args[1], args[2], new String(password));
                System.out.println("Amministratore demo creato con ID " + id);
            } finally {
                Arrays.fill(password, '\0');
                connectionProvider.close();
            }
            return;
        }
        if (args.length > 0) {
            throw new IllegalArgumentException("Argomenti non riconosciuti");
        }

        JwtSigningKeyStore jwtSigningKeyStore = new JwtSigningKeyStore(Path.of(config.jwtSigningKeyPath()));
        JwtConfig jwtConfig = new JwtConfig(jwtSigningKeyStore.keyMaterial(), config.raw());
        JwtTokenService jwtTokenService = new JwtTokenService(jwtConfig);

        UserDao userDao = new UserDao(connectionProvider);
        PlayerProfileDao playerProfileDao = new PlayerProfileDao(connectionProvider);
        RefreshTokenDao refreshTokenDao = new RefreshTokenDao(connectionProvider);
        PasswordHasher passwordHasher = new PasswordHasher();

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;
        UserEventPublisher userEventPublisher = new UserEventPublisher(eventBus);
        backfillConteggioUtenti(userDao, userEventPublisher);

        UserService userService = new UserService(connectionProvider, userDao, playerProfileDao, passwordHasher,
                userEventPublisher);
        AuthService authService = new AuthService(connectionProvider, userDao, refreshTokenDao, passwordHasher,
                jwtTokenService, jwtConfig);
        DeviceTokenService deviceTokenService = new DeviceTokenService(connectionProvider, userDao, passwordHasher,
                jwtTokenService);

        Spark.port(config.port());
        SparkExceptionHandlers.register();

        new AuthController(authService, userService, jwtTokenService, jwtConfig, jwtSigningKeyStore,
                config.internalServiceSecret()).register();
        new UserController(userService, deviceTokenService, jwtTokenService,
                () -> mqttClient != null && mqttClient.isConnected()).register();

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("user-service avviato sulla porta {}", config.port());
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
     * Pubblica il conteggio degli utenti già presenti per riallineare le statistiche.
     *
     * @param userDao componente {@link it.uniupo.pissir.darts.user.dao.UserDao} utilizzato dall'operazione
     * @param userEventPublisher componente {@link it.uniupo.pissir.darts.user.mqtt.UserEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.user.dao.UserDao
     *  
     */
    private static void backfillConteggioUtenti(UserDao userDao, UserEventPublisher userEventPublisher) {
        int pagina = 0;
        List<User> lotto;
        do {
            lotto = userDao.search(null, null, Page.of(pagina, 100));
            for (User utente : lotto) {
                if (utente.contaAiFiniStatistiche()) {
                    userEventPublisher.publishBackfill(utente.id());
                }
            }
            pagina++;
        } while (!lotto.isEmpty());
    }
}
