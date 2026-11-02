package it.uniupo.pissir.darts.gateway;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.mqtt.MqttConnectionFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtConfigFactory;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import it.uniupo.pissir.darts.gateway.config.RouteTable;
import it.uniupo.pissir.darts.gateway.config.SslConfig;
import it.uniupo.pissir.darts.gateway.filter.AccessLogFilter;
import it.uniupo.pissir.darts.gateway.filter.CorsFilter;
import it.uniupo.pissir.darts.gateway.filter.JwtSecurityFilter;
import it.uniupo.pissir.darts.gateway.monitoring.BrokerProbe;
import it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator;
import it.uniupo.pissir.darts.gateway.monitoring.SystemStatusController;
import it.uniupo.pissir.darts.gateway.proxy.ReverseProxyHandler;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Route;
import spark.Spark;

import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Avvia il componente {@code api-gateway} e ne collega le dipendenze. 
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final List<String> PREFISSI_INSTRADATI = List.of("auth", "users", "locali", "bersagli",
            "edge-devices", "giochi", "sensori", "regole", "partite", "squadre", "tornei", "statistiche");
    private static final Map<String, java.util.function.BiConsumer<String, Route>> METODI_PROXY = Map.of(
            "GET", Spark::get, "POST", Spark::post, "PUT", Spark::put, "PATCH", Spark::patch, "DELETE", Spark::delete);

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
        GatewayConfig config = GatewayConfig.load();

        JwtConfig jwtConfig = JwtConfigFactory.forVerifier(config.raw());
        JwtTokenService jwtTokenService = new JwtTokenService(jwtConfig);

        RouteTable routeTable = new RouteTable(config);
        JwtSecurityFilter jwtSecurityFilter = new JwtSecurityFilter(jwtTokenService, routeTable);
        CorsFilter corsFilter = new CorsFilter(config.corsAllowedOrigins());
        ReverseProxyHandler reverseProxyHandler = new ReverseProxyHandler(config.proxyTimeout());

        MqttClient mqttClient = connectMqtt(config);
        MqttEventBus eventBus = mqttClient != null ? new MqttEventBus(mqttClient) : null;
        BrokerProbe brokerProbe = new BrokerProbe(eventBus);

        Map<String, String> baseUrlPerServizio = Map.of(
                "user-service", config.userServiceBaseUrl(),
                "locale-service", config.localeServiceBaseUrl(),
                "game-service", config.gameServiceBaseUrl(),
                "match-service", config.matchServiceBaseUrl(),
                "tournament-service", config.tournamentServiceBaseUrl(),
                "statistics-service", config.statisticsServiceBaseUrl());
        HealthAggregator healthAggregator = new HealthAggregator(baseUrlPerServizio, config.healthCheckTimeout());

        SslConfig.apply(config);
        Spark.staticFiles.location("/public");
        Spark.get("/client-config.json", (req, res) -> {
            res.type("application/json");
            res.header("Cache-Control", "no-store");
            return JsonSupport.toJson(Map.of("mqttWebSocketUrl", config.raw().getProperty("web.mqttWebSocketUrl", "auto")));
        });
        SparkExceptionHandlers.register();

        Spark.before((req, res) -> {
            if (corsFilter.applica(req, res)) {

                return;
            }
            AccessLogFilter.before(req, res);
            if (!jwtSecurityFilter.isPubblico(req)) {
                jwtSecurityFilter.verifica(req);
            }
        });
        Spark.after(AccessLogFilter::after);

        for (String prefisso : PREFISSI_INSTRADATI) {
            for (var metodo : METODI_PROXY.values()) {
                metodo.accept("/" + prefisso,
                        (req, res) -> reverseProxyHandler.inoltra(req, res, routeTable.risolvi(req.pathInfo()).orElseThrow()));
                metodo.accept("/" + prefisso + "/*",
                        (req, res) -> reverseProxyHandler.inoltra(req, res, routeTable.risolvi(req.pathInfo()).orElseThrow()));
            }

            Spark.options("/" + prefisso, (req, res) -> "");
            Spark.options("/" + prefisso + "/*", (req, res) -> "");
        }

        new SystemStatusController(healthAggregator, brokerProbe, config, jwtTokenService).register();
        new LocalEdgeRegistrationController(config, jwtTokenService, Path.of(".")).register();
        new SimulatorGatewayController(config, jwtTokenService, Path.of(".")).register();

        Instant startedAt = Instant.now();
        Spark.get("/health", (req, res) -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            res.type("application/json");
            return JsonSupport.toJson(new HealthDTO("api-gateway", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK,
                    brokerProbe.verifica() ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO));
        });

        Spark.init();
        Spark.awaitInitialization();
        LOG.info("api-gateway avviato — {} {}", config.localHttpEnabled() ? "HTTP loopback" : "HTTPS",
                config.localHttpEnabled() ? config.insecurePort() : config.securePort());
    }

    /**
     * Prepara e apre il collegamento al broker MQTT.
     *
     * @param config configurazione del componente
     * @return l'oggetto {@link org.eclipse.paho.client.mqttv3.MqttClient} prodotto o recuperato
     * @see org.eclipse.paho.client.mqttv3.MqttClient
     *  
     */
    private static MqttClient connectMqtt(GatewayConfig config) {
        try {
            return MqttConnectionFactory.connect(config.mqttBrokerUrl(), config.mqttClientId(),
                    config.mqttUsername(), config.mqttPassword(), null);
        } catch (MqttException e) {
            LOG.warn("Impossibile connettersi al broker MQTT {}: {} — GET /system/health riporterà broker=DISCONNESSO",
                    config.mqttBrokerUrl(), e.getMessage());
            return null;
        }
    }
}
