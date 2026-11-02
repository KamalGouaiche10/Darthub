package it.uniupo.pissir.darts.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtSigningKeyStore;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spark.Spark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica il comportamento di {@code SimulatorGatewayController} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class SimulatorGatewayControllerTest {
    @TempDir Path root;
    private HttpServer sensor;
    private HttpServer locales;
    private long boardId;
    private String baseUrl;
    private String token;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger sensorCalls = new AtomicInteger();
    private final AtomicInteger boardStatus = new AtomicInteger(200);
    private final AtomicReference<String> boardAuthorization = new AtomicReference<>();
    private final AtomicReference<String> sensorAuthorization = new AtomicReference<>();
    private final AtomicReference<String> throwBody = new AtomicReference<>();
    private final AtomicReference<String> throwQuery = new AtomicReference<>();

    /**
     * Prepara le risorse e i dati necessari ai test.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @BeforeEach
    void start() throws Exception {
        sensor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int sensorPort = sensor.getAddress().getPort();
        boardId = sensorPort - 7400;
        sensor.createContext("/simulatore/stato", exchange -> {
            sensorCalls.incrementAndGet();
            sensorAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            json(exchange, 200, "{\"localeId\":2,\"dartboardId\":" + boardId + ",\"matchId\":4}");
        });
        sensor.createContext("/simulatore/tiro", exchange -> {
            sensorAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            throwBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            throwQuery.set(exchange.getRequestURI().getQuery());
            json(exchange, "matchId=4".equals(throwQuery.get()) ? 200 : 409, "{\"punti\":20}");
        });
        sensor.start();
        locales = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locales.createContext("/bersagli/", exchange -> {
            boardAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            json(exchange, boardStatus.get(), boardStatus.get() == 200
                    ? "{\"id\":" + boardId + ",\"localeId\":2}" : "{\"messaggio\":\"Accesso negato\"}");
        });
        locales.start();
        Path configFile = root.resolve("gateway.properties");
        Files.writeString(configFile, "services.locale.baseUrl=http://127.0.0.1:" + locales.getAddress().getPort());
        String previous = System.getProperty("config.file");
        GatewayConfig config;
        System.setProperty("config.file", configFile.toString());
        try { config = GatewayConfig.load(); }
        finally {
            if (previous == null) System.clearProperty("config.file");
            else System.setProperty("config.file", previous);
        }
        JwtTokenService tokens = new JwtTokenService(new JwtConfig(JwtSigningKeyStore.ephemeral().keyMaterial(), new Properties()));
        token = tokens.generateAccessToken(5, "test-player", "PLAYER");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Spark.port(port);
        SparkExceptionHandlers.register();
        new SimulatorGatewayController(config, tokens, root).register();
        Spark.init();
        Spark.awaitInitialization();
        baseUrl = "http://127.0.0.1:" + port;
    }

    /**
     * Chiude e rilascia le risorse usate dai test. 
     */
    @AfterEach
    void stop() {
        Spark.stop();
        Spark.awaitStop();
        if (sensor != null) sensor.stop(0);
        if (locales != null) locales.stop(0);
    }

    /**
     * Verifica che il gateway esponga il pannello senza rendere pubblici host e porta del sensore.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void returnsSitePathAndServesPanelWithoutExposingSensorHostOrPort() throws Exception {
        HttpResponse<String> opened = request("POST", "/simulators/open", "{\"dartboardId\":" + boardId + "}", true);
        assertEquals(200, opened.statusCode());
        assertTrue(opened.body().contains("/simulator/" + boardId + "/"));
        assertFalse(opened.body().contains("127.0.0.1"));
        assertFalse(opened.body().contains("localhost"));
        assertEquals("Bearer " + token, boardAuthorization.get());
        HttpResponse<String> page = request("GET", "/simulator/" + boardId + "/", null, false);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Bersaglio interattivo"));
        HttpResponse<String> script = request("GET", "/simulator/" + boardId + "/js/simulatorClient.js", null, false);
        assertEquals(200, script.statusCode());
        assertTrue(script.body().contains("sessionStorage"));
        assertEquals(404, request("GET", "/simulator/" + boardId + "/gateway.properties", null, false).statusCode());
    }

    /**
     * Verifica l'autenticazione dei comandi e l'assenza del token del cliente nelle richieste al sensore.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void authenticatesStateAndCommandsAndNeverSendsCustomerTokenToSensor() throws Exception {
        String state = "/simulator/" + boardId + "/simulatore/stato";
        assertEquals(401, request("GET", state, null, false).statusCode());
        assertEquals(401, request("POST", "/simulators/open", "{\"dartboardId\":" + boardId + "}", false).statusCode());
        assertEquals(200, request("GET", state, null, true).statusCode());
        String body = "{\"settore\":\"S20\",\"moltiplicatore\":\"SINGLE\"}";
        assertEquals(200, request("POST", "/simulator/" + boardId + "/simulatore/tiro?matchId=4", body, true).statusCode());
        assertEquals(body, throwBody.get());
        assertEquals("matchId=4", throwQuery.get());
        assertNull(sensorAuthorization.get());
        assertEquals(409, request("POST", "/simulator/" + boardId + "/simulatore/tiro?matchId=77", body, true).statusCode());
    }

    /**
     * Verifica che richieste non autorizzate e identificativi errati non raggiungano il sensore.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void forbiddenBoardNeverReachesSensorAndInvalidIdsAreRejected() throws Exception {
        boardStatus.set(403);
        assertEquals(403, request("POST", "/simulators/open", "{\"dartboardId\":" + boardId + "}", true).statusCode());
        assertEquals(403, request("GET", "/simulator/" + boardId + "/simulatore/stato", null, true).statusCode());
        assertEquals(0, sensorCalls.get());
        assertEquals(400, request("POST", "/simulators/open", "{\"dartboardId\":1.2}", true).statusCode());
        assertEquals(400, request("POST", "/simulators/open", "{}", true).statusCode());
        assertEquals(404, request("POST", "/simulator/" + boardId + "/simulatore/not-a-command", "{}", true).statusCode());
    }

    /**
     * Esegue la richiesta HTTP con i parametri e le credenziali indicati.
     *
     * @param method metodo HTTP della richiesta
     * @param path percorso o indirizzo della risorsa
     * @param body corpo della richiesta o della risposta
     * @param authenticated indicatore di richiesta autenticata
     * @return la risposta ricevuta dal servizio destinatario
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     * @see java.net.http.HttpResponse
     *  
     */
    private HttpResponse<String> request(String method, String path, String body, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (authenticated) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Invia al client la risposta JSON con il codice HTTP richiesto.
     *
     * @param exchange scambio HTTP del server di prova
     * @param status codice di stato HTTP
     * @param body corpo della richiesta o della risposta
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @see com.sun.net.httpserver.HttpExchange
     *  
     */
    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
