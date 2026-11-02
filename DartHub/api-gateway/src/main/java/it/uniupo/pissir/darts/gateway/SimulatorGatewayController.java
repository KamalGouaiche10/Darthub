package it.uniupo.pissir.darts.gateway;

import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
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
import java.util.Map;
import java.util.Set;

/**
 * Espone il pannello e i comandi dei simulatori attraverso il gateway.
 *
 * @see java.util.Set
 *  
 */
public final class SimulatorGatewayController {
    private static final Set<String> ASSETS = Set.of("index.html", "css/style.css", "css/tailwind.css",
            "js/app.js", "js/simulatorClient.js", "components/dartboardTarget.js");
    private static final Set<String> ACTIONS = Set.of("avvia", "ferma", "velocita", "scenario", "tiro", "disconnetti");
    private final GatewayConfig config;
    private final JwtTokenService tokens;
    private final LocalSimulatorProvisioner simulators;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    /**
     * Inizializza {@code SimulatorGatewayController} con i dati e le dipendenze indicati.
     *
     * @param config configurazione del componente
     * @param tokens tokens da utilizzare
     * @param root cartella radice del progetto
     * @see it.uniupo.pissir.darts.gateway.config.GatewayConfig
     *  
     */
    public SimulatorGatewayController(GatewayConfig config, JwtTokenService tokens, Path root) {
        this.config = config;
        this.tokens = tokens;
        this.simulators = new LocalSimulatorProvisioner(root);
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/simulators/open", this::open);
        Spark.get("/simulator/:id/simulatore/stato", this::forward);
        Spark.post("/simulator/:id/simulatore/:action", (req, res) -> {
            if (!ACTIONS.contains(req.params(":action"))) {
                res.status(404);
                return "";
            }
            return forward(req, res);
        });
        Spark.get("/simulator/:id/", (req, res) -> asset("index.html", res));
        Spark.get("/simulator/:id/*", (req, res) -> asset(req.splat()[0], res));
    }

    /**
     * Prepara il simulatore richiesto e restituisce il percorso del pannello.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Bersaglio non valido»  
     *  
     */
    private Object open(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), tokens);
        JsonObject input = JsonSupport.fromJson(req.body(), JsonObject.class);
        long boardId;
        try { boardId = input.get("dartboardId").getAsBigDecimal().longValueExact(); }
        catch (RuntimeException e) { throw new ValidationException("Bersaglio non valido"); }
        if (boardId <= 0) throw new ValidationException("Bersaglio non valido");
        HttpResponse<byte[]> board = board(req, boardId);
        if (board.statusCode() != 200) return respond(board, res);
        ensure(board, boardId);
        res.type("application/json");
        res.header("Cache-Control", "no-store");
        return JsonSupport.toJson(Map.of("simulatorUrl", "/simulator/" + boardId + "/"));
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Bersaglio non valido»  
     *  
     */
    private Object forward(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), tokens);
        long boardId;
        try { boardId = Long.parseLong(req.params(":id")); }
        catch (NumberFormatException e) { throw new ValidationException("Bersaglio non valido"); }
        if (boardId <= 0) throw new ValidationException("Bersaglio non valido");
        HttpResponse<byte[]> board = board(req, boardId);
        if (board.statusCode() != 200) return respond(board, res);
        String baseUrl = ensure(board, boardId);
        String action = "GET".equals(req.requestMethod()) ? "stato" : req.params(":action");
        String query = req.queryString();
        String url = baseUrl + "/simulatore/" + action + (query == null ? "" : "?" + query);
        byte[] body = req.bodyAsBytes();
        HttpRequest upstream = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(req.requestMethod(), body == null || body.length == 0
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return respond(send(upstream), res);
    }

    /**
     * Recupera i dati del bersaglio usando l'autorizzazione del cliente.
     *
     * @param req richiesta HTTP ricevuta
     * @param boardId identificativo del bersaglio
     * @return la risposta ricevuta dal servizio destinatario
     * @see java.net.http.HttpResponse
     *  
     */
    private HttpResponse<byte[]> board(Request req, long boardId) {
        return send(HttpRequest.newBuilder(URI.create(config.localeServiceBaseUrl() + "/bersagli/" + boardId))
                .timeout(Duration.ofSeconds(10)).header("Authorization", req.headers("Authorization")).GET().build());
    }

    /**
     * Recupera l'istanza del simulatore oppure ne avvia una nuova.
     *
     * @param boardResponse risposta del servizio contenente il bersaglio
     * @param boardId identificativo del bersaglio
     * @return il testo o il valore di configurazione richiesto
     * @see java.net.http.HttpResponse
     *  
     */
    private String ensure(HttpResponse<byte[]> boardResponse, long boardId) {
        JsonObject board = JsonSupport.fromJson(new String(boardResponse.body(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        try {
            return simulators.ensure(board.get("localeId").getAsLong(), boardId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Avvio del simulatore interrotto");
        } catch (IOException | IllegalArgumentException e) {
            throw new ServiceUnavailableException(e.getMessage());
        }
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param request richiesta da elaborare
     * @return la risposta ricevuta dal servizio destinatario
     * @see java.net.http.HttpResponse
     *  
     */
    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return client.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Collegamento al simulatore interrotto");
        } catch (IOException e) {
            throw new ServiceUnavailableException("Il servizio richiesto non è al momento raggiungibile");
        }
    }

    /**
     * Invia la risposta HTTP con lo stato e il contenuto indicati.
     *
     * @param upstream risposta del servizio a valle
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @see java.net.http.HttpResponse
     *  
     */
    private Object respond(HttpResponse<byte[]> upstream, Response res) {
        res.status(upstream.statusCode());
        res.type(upstream.headers().firstValue("Content-Type").orElse("application/json"));
        res.header("Cache-Control", "no-store");
        try {
            res.raw().getOutputStream().write(upstream.body());
            res.raw().getOutputStream().flush();
            return "";
        } catch (IOException e) { throw new ServiceUnavailableException("Risposta del simulatore interrotta"); }
    }

    /**
     * Restituisce una risorsa pubblica del pannello del simulatore.
     *
     * @param name nome del componente o della risorsa
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @see spark.Response
     *  
     */
    private Object asset(String name, Response res) {
        if (!ASSETS.contains(name)) { res.status(404); return ""; }
        try (var in = SimulatorGatewayController.class.getResourceAsStream("/simulator-web/" + name)) {
            if (in == null) { res.status(404); return ""; }
            res.type(name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".css") ? "text/css; charset=utf-8" : "application/javascript; charset=utf-8");
            res.header("Cache-Control", "no-cache");
            res.raw().getOutputStream().write(in.readAllBytes());
            res.raw().getOutputStream().flush();
            return "";
        } catch (IOException e) { throw new ServiceUnavailableException("Pannello del simulatore non disponibile"); }
    }
}
