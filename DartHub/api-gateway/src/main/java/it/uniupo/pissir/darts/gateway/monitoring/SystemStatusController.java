package it.uniupo.pissir.darts.gateway.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Espone lo stato aggregato dei componenti della piattaforma.
 *
 * @see com.google.gson.Gson
 *  
 */
public final class SystemStatusController {

    private static final Gson GSON = GsonFactory.instance();

    private final HealthAggregator healthAggregator;
    private final BrokerProbe brokerProbe;
    private final GatewayConfig config;
    private final JwtTokenService jwtTokenService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Inizializza {@code SystemStatusController} con i dati e le dipendenze indicati.
     *
     * @param healthAggregator health aggregator da utilizzare
     * @param brokerProbe broker probe da utilizzare
     * @param config configurazione del componente
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator
     *  
     */
    public SystemStatusController(HealthAggregator healthAggregator, BrokerProbe brokerProbe, GatewayConfig config,
                                   JwtTokenService jwtTokenService) {
        this.healthAggregator = healthAggregator;
        this.brokerProbe = brokerProbe;
        this.config = config;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/system/health", this::health);
        Spark.get("/system/edge-status", this::edgeStatus);
        Spark.get("/system/metriche", this::metriche);
    }

    /**
     * Restituisce lo stato del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object health(Request req, Response res) {
        requirePlatformAdmin(req);
        List<Map<String, Object>> servizi = healthAggregator.interroga().stream()
                .map(esito -> {
                    Map<String, Object> riga = new java.util.LinkedHashMap<>();
                    riga.put("servizio", esito.nome());
                    riga.put("raggiungibile", esito.raggiungibile());
                    riga.put("dettaglio", esito.raggiungibile() ? GSON.fromJson(esito.corpo(), JsonObject.class) : esito.corpo());
                    return riga;
                })
                .toList();
        res.type("application/json");
        return JsonSupport.toJson(Map.of("servizi", servizi, "broker", brokerProbe.verifica() ? "CONNESSO" : "DISCONNESSO"));
    }

    /**
     * Recupera lo stato dei dispositivi edge.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object edgeStatus(Request req, Response res) {
        requirePlatformAdmin(req);
        String token = req.headers("Authorization");
        JsonObject risposta = get(config.localeServiceBaseUrl() + "/edge-devices?size=100", token);
        res.type("application/json");
        return JsonSupport.toJson(risposta);
    }

    /**
     * Compone le metriche correnti del sistema.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object metriche(Request req, Response res) {
        requirePlatformAdmin(req);
        String token = req.headers("Authorization");
        long partiteAttive = contaPartiteAttive(token);
        int tiriInCodaEdge = sommaTiriInCoda(token);

        res.type("application/json");
        return JsonSupport.toJson(Map.of("partiteAttive", partiteAttive, "tiriUltimoMinuto", 0, "tiriInCodaEdge", tiriInCodaEdge));
    }

    /**
     * Conta partite attive per il system stato.
     *
     * @param token token da utilizzare
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    private long contaPartiteAttive(String token) {
        JsonObject risposta = get(config.matchServiceBaseUrl() + "/partite?stato=IN_CORSO&size=1", token);
        return risposta != null && risposta.has("totaleElementi") ? risposta.get("totaleElementi").getAsLong() : 0;
    }

    /**
     * Somma i tiri che gli edge dichiarano in attesa di sincronizzazione.
     *
     * @param token token da utilizzare
     * @return il valore numerico calcolato o letto
     *  
     */
    private int sommaTiriInCoda(String token) {
        JsonObject risposta = get(config.localeServiceBaseUrl() + "/edge-devices?size=100", token);
        if (risposta == null || !risposta.has("contenuto")) {
            return 0;
        }
        JsonArray contenuto = risposta.getAsJsonArray("contenuto");
        int totale = 0;
        for (var elemento : contenuto) {
            JsonObject device = elemento.getAsJsonObject();
            if (device.has("tiriInCoda")) {
                totale += device.get("tiriInCoda").getAsInt();
            }
        }
        return totale;
    }

    /**
     * Legge il system stato tramite la richiesta HTTP.
     *
     * @param url indirizzo della risorsa HTTP
     * @param bearerToken token Bearer della chiamata
     * @return l'oggetto {@link com.google.gson.JsonObject} prodotto o recuperato
     * @see com.google.gson.JsonObject
     *  
     */
    private JsonObject get(String url, String bearerToken) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            if (bearerToken != null) {
                builder.header("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return GSON.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifica il requisito platform amministratore prima di eseguire l'operazione.
     *
     * @param req richiesta HTTP ricevuta  
     *  
     */
    private void requirePlatformAdmin(Request req) {
        AuthenticatedUser user = (AuthenticatedUser) req.attribute(it.uniupo.pissir.darts.gateway.filter.JwtSecurityFilter.ATTR_USER);
        if (user == null) {
            user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        }
        RequestAuthentication.requireAnyRole(user, "PLATFORM_ADMIN");
    }
}
