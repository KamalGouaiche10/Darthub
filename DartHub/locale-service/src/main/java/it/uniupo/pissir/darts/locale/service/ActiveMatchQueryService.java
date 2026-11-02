package it.uniupo.pissir.darts.locale.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consulta le partite attive prima delle modifiche a locali e bersagli. 
 */
public final class ActiveMatchQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMatchQueryService.class);

    private final ServiceHttpClient httpClient;
    private final String matchServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

    /**
     * Inizializza {@code ActiveMatchQueryService} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param matchServiceBaseUrl indirizzo di base della partita servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public ActiveMatchQueryService(ServiceHttpClient httpClient, String matchServiceBaseUrl,
                                    ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.matchServiceBaseUrl = matchServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Recupera le partite attive del locale.
     *
     * @param localeId identificativo del locale
     * @return l'oggetto {@link com.google.gson.JsonObject} prodotto o recuperato
     * @see com.google.gson.JsonObject
     *  
     */
    public JsonObject activeMatches(long localeId) {
        JsonObject wrapper = new JsonObject();
        try {
            String url = matchServiceBaseUrl + "/partite?localeId=" + localeId + "&stato=IN_CORSO";
            JsonElement result = httpClient.get(url, serviceTokenClient.token(), JsonElement.class);
            wrapper.add("contenuto", result);
            wrapper.addProperty("avvisoServizioNonRaggiungibile", false);
        } catch (RuntimeException e) {
            LOG.warn("match-service non raggiungibile per il locale {}: {}", localeId, e.getMessage());
            wrapper.add("contenuto", new JsonArray());
            wrapper.addProperty("avvisoServizioNonRaggiungibile", true);
        }
        return wrapper;
    }

    /**
     * Impedisce la modifica del bersaglio quando esiste una partita attiva.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     *  
     */
    public void requireNoActiveMatch(long localeId, long dartboardId) {
        try {
            int page = 0;
            while (true) {
                String url = matchServiceBaseUrl + "/partite?localeId=" + localeId
                        + "&stato=IN_CORSO&page=" + page + "&size=100";
                JsonObject result = httpClient.get(url, serviceTokenClient.token(), JsonObject.class);
                JsonArray matches = result.getAsJsonArray("contenuto");
                for (JsonElement element : matches) {
                    if (element.getAsJsonObject().get("dartboardId").getAsLong() == dartboardId) {
                        throw new ConflictException("PARTITA_IN_CORSO",
                                "Bersaglio occupato da una partita in corso; terminarla prima di liberarlo");
                    }
                }
                if (matches.size() < 100) return;
                page++;
            }
        } catch (ConflictException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Verifica partite attive fallita per il bersaglio {}: {}", dartboardId, e.getMessage());
            throw new ConflictException("PARTITE_NON_VERIFICABILI",
                    "Impossibile verificare le partite in corso; bersaglio non modificato");
        }
    }
}
