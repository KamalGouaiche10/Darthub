package it.uniupo.pissir.darts.gateway.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raccoglie lo stato dei servizi interrogando i rispettivi endpoint di salute.
 *
 * @see java.util.Map
 *  
 */
public final class HealthAggregator {

    /**
     * Raccoglie i dati di esito servizio.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param nome nome della risorsa
     * @param raggiungibile indicatore di raggiungibilitÃ  del componente
     * @param corpo corpo del messaggio
     *  
     */
    public record EsitoServizio(String nome, boolean raggiungibile, String corpo) {
    }

    private final Map<String, String> baseUrlPerServizio;
    private final Duration timeout;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "health-aggregator");
        t.setDaemon(true);
        return t;
    });

    /**
     * Inizializza {@code HealthAggregator} con i dati e le dipendenze indicati.
     *
     * @param baseUrlPerServizio insieme dei dati relativi a base url per servizio
     * @param timeout tempo massimo di attesa
     * @see java.util.Map
     *  
     */
    public HealthAggregator(Map<String, String> baseUrlPerServizio, Duration timeout) {
        this.baseUrlPerServizio = new LinkedHashMap<>(baseUrlPerServizio);
        this.timeout = timeout;
    }

    /**
     * Interroga i componenti e raccoglie il loro stato di disponibilità.
     *
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<EsitoServizio> interroga() {
        List<CompletableFuture<EsitoServizio>> futures = baseUrlPerServizio.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> controlla(entry.getKey(), entry.getValue()), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /**
     * Esegue il controllo di disponibilità del broker.
     *
     * @param nome nome della risorsa
     * @param baseUrl indirizzo di base del servizio
     * @return l'oggetto {@link it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator.EsitoServizio} prodotto o recuperato
     * @see it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator.EsitoServizio
     *  
     */
    private EsitoServizio controlla(String nome, String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(timeout).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new EsitoServizio(nome, true, response.body());
            }
            return new EsitoServizio(nome, false, "HTTP " + response.statusCode());
        } catch (Exception e) {
            return new EsitoServizio(nome, false, "non raggiungibile: " + e.getMessage());
        }
    }
}
