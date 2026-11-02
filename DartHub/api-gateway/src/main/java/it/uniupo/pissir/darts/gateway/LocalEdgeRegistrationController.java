package it.uniupo.pissir.darts.gateway;

import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Espone le operazioni locali di registrazione e avvio dei dispositivi edge. 
 */
public final class LocalEdgeRegistrationController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalEdgeRegistrationController.class);
    private final GatewayConfig config;
    private final JwtTokenService jwtTokens;
    private final LocalEdgeProvisioner provisioner;
    private final LocalSimulatorProvisioner simulators;
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Inizializza {@code LocalEdgeRegistrationController} con i dati e le dipendenze indicati.
     *
     * @param config configurazione del componente
     * @param jwtTokens servizio di generazione e verifica dei JWT
     * @param root cartella radice del progetto
     * @see it.uniupo.pissir.darts.gateway.config.GatewayConfig
     *  
     */
    public LocalEdgeRegistrationController(GatewayConfig config, JwtTokenService jwtTokens, Path root) {
        this.config = config;
        this.jwtTokens = jwtTokens;
        this.provisioner = new LocalEdgeProvisioner(root);
        this.simulators = new LocalSimulatorProvisioner(root);
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        if (config.localHttpEnabled()) {
            Spark.post("/local-edge/register", this::registerAndStart);
            Spark.post("/local-edge/simulator", this::ensureSimulator);
            Spark.delete("/local-edge/:id", this::deleteAndStop);
        }
    }

    /**
     * Richiede l'avvio o il riuso del simulatore del bersaglio.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Bersaglio non valido»  
     *  
     */
    private Object ensureSimulator(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokens);
        JsonObject input = JsonSupport.fromJson(req.body(), JsonObject.class);
        long boardId;
        try {
            boardId = input.get("dartboardId").getAsLong();
        } catch (RuntimeException e) {
            throw new ValidationException("Bersaglio non valido");
        }
        if (boardId <= 0) throw new ValidationException("Bersaglio non valido");
        HttpResponse<String> boardResponse = send("GET", "/bersagli/" + boardId, null,
                req.headers("Authorization"));
        if (boardResponse.statusCode() != 200) return passThrough(boardResponse, res);
        JsonObject board = JsonSupport.fromJson(boardResponse.body(), JsonObject.class);
        try {
            String url = simulators.ensure(board.get("localeId").getAsLong(), boardId);
            res.type("application/json");
            res.header("Cache-Control", "no-store");
            return JsonSupport.toJson(java.util.Map.of("simulatorUrl", url));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Avvio del simulatore interrotto");
        } catch (IOException | IllegalArgumentException e) {
            throw new ServiceUnavailableException(e.getMessage());
        }
    }

    /**
     * Ferma l'edge locale ed elimina la registrazione del dispositivo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «ID dispositivo non valido»  
     *  
     */
    private Object deleteAndStop(Request req, Response res) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokens);
        RequestAuthentication.requireAnyRole(user, "LOCALE_ADMIN", "PLATFORM_ADMIN");
        long deviceId;
        try {
            deviceId = Long.parseLong(req.params(":id"));
        } catch (NumberFormatException e) {
            throw new ValidationException("ID dispositivo non valido");
        }
        if (deviceId <= 0) throw new ValidationException("ID dispositivo non valido");
        HttpResponse<String> current = send("GET", "/edge-devices/" + deviceId, null,
                req.headers("Authorization"));
        if (current.statusCode() != 200) return passThrough(current, res);
        JsonObject device = JsonSupport.fromJson(current.body(), JsonObject.class);
        long localeId = device.get("localeId").getAsLong();
        long boardId = device.get("dartboardId").getAsLong();
        try {
            provisioner.stop(deviceId, localeId, boardId);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile arrestare l'edge locale: " + e.getMessage(), e);
        }
        HttpResponse<String> deleted = send("DELETE", "/edge-devices/" + deviceId, null,
                req.headers("Authorization"));
        if (deleted.statusCode() != 204) return passThrough(deleted, res);
        try {
            provisioner.remove(deviceId, localeId, boardId);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Dispositivo {} eliminato ma pulizia dei file locali non riuscita: {}", deviceId, e.getMessage());
        }
        res.status(204);
        return "";
    }

    /**
     * Registra il dispositivo edge e ne avvia l'istanza locale.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Bersaglio e seriale sono obbligatori»  
     *  
     */
    private Object registerAndStart(Request req, Response res) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokens);
        RequestAuthentication.requireAnyRole(user, "LOCALE_ADMIN", "PLATFORM_ADMIN", "SERVICE");
        JsonObject input = JsonSupport.fromJson(req.body(), JsonObject.class);
        if (input == null || !input.has("dartboardId") || !input.has("serialNumber")) {
            throw new ValidationException("Bersaglio e seriale sono obbligatori");
        }
        long boardId = input.get("dartboardId").getAsLong();
        if (boardId <= 0) throw new ValidationException("Bersaglio non valido");
        String authorization = req.headers("Authorization");
        HttpResponse<String> boardResponse = send("GET", "/bersagli/" + boardId, null, authorization);
        if (boardResponse.statusCode() != 200) return passThrough(boardResponse, res);
        JsonObject board = JsonSupport.fromJson(boardResponse.body(), JsonObject.class);
        long localeId = board.get("localeId").getAsLong();
        long gameTypeId = board.get("gameTypeId").getAsLong();

        HttpResponse<String> registration = send("POST", "/edge-devices/register", req.body(), authorization);
        if (registration.statusCode() != 201) return passThrough(registration, res);
        JsonObject registered = JsonSupport.fromJson(registration.body(), JsonObject.class);
        long deviceId = registered.get("deviceId").getAsLong();
        String token = registered.get("token").getAsString();
        JsonObject edgeConfig = registered.getAsJsonObject("config");
        try {
            int port = provisioner.provision(deviceId, localeId, boardId, gameTypeId,
                    input.get("serialNumber").getAsString(), token,
                    edgeConfig.get("intervalloHeartbeatS").getAsInt(),
                    !edgeConfig.has("urlBrokerLocale") || edgeConfig.get("urlBrokerLocale").isJsonNull()
                            ? null : edgeConfig.get("urlBrokerLocale").getAsString());
            res.status(201);
            res.type("application/json");
            return JsonSupport.toJson(new RegistrationResult(deviceId, true,
                    "http://127.0.0.1:" + port + "/", null, null));
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("Avvio automatico edge {} non riuscito: {}", deviceId, e.getMessage());
            res.status(201);
            res.type("application/json");
            return JsonSupport.toJson(new RegistrationResult(deviceId, false, null, token,
                    "Avvio automatico non riuscito: " + e.getMessage()));
        }
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param method metodo HTTP della richiesta
     * @param path percorso o indirizzo della risorsa
     * @param body corpo della richiesta o della risposta
     * @param authorization valore dell'header di autorizzazione
     * @return la risposta ricevuta dal servizio destinatario
     * @see java.net.http.HttpResponse
     *  
     */
    private HttpResponse<String> send(String method, String path, String body, String authorization) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.localeServiceBaseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ServiceUnavailableException("locale-service non raggiungibile");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Registrazione edge interrotta");
        }
    }

    /**
     * Trasferisce stato e contenuto della risposta al client HTTP.
     *
     * @param source origine dei dati
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @see java.net.http.HttpResponse
     *  
     */
    private Object passThrough(HttpResponse<String> source, Response res) {
        res.status(source.statusCode());
        res.type("application/json");
        return source.body();
    }

    /**
     * Descrive l'esito della registrazione e dell'avvio di un edge locale.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param deviceId identificativo del dispositivo
     * @param autoProvisioned indicatore di avvio automatico dell'edge locale
     * @param monitorUrl indirizzo del monitor locale dell'edge
     * @param token token da utilizzare
     * @param error messaggio dell'errore riscontrato
     *  
     */
    private record RegistrationResult(long deviceId, boolean autoProvisioned,
                                      String monitorUrl, String token, String error) {
    }
}
