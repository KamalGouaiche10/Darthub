package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recupera da game-service il motore di un gameTypeId, in cache in memoria: risolve quale RuleEngine usare per una partita, stesso pattern e stessa autenticazione interna di ScoringRuleClient.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class GameTypeClient {

    private final ServiceHttpClient httpClient;
    private final String gameServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;
    private final Map<Long, String> cacheMotore = new ConcurrentHashMap<>();

    /**
     * Inizializza {@code GameTypeClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param gameServiceBaseUrl indirizzo di base del gioco servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public GameTypeClient(ServiceHttpClient httpClient, String gameServiceBaseUrl, ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.gameServiceBaseUrl = gameServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Recupera motore per il gioco.
     *
     * @param gameTypeId identificativo del gioco
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String fetchMotore(long gameTypeId) {
        return cacheMotore.computeIfAbsent(gameTypeId, this::fetchMotoreRemoto);
    }

    /**
     * Recupera motore remoto per il gioco.
     *
     * @param id identificativo del gioco
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    private String fetchMotoreRemoto(long id) {
        RemoteGameType remote = httpClient.get(gameServiceBaseUrl + "/giochi/" + id, serviceTokenClient.token(), RemoteGameType.class);
        return remote.motore();
    }

    /**
     * Stessa forma del GameTypeDTO di game-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del remoto gioco tipo
     * @param nome nome della risorsa
     * @param descrizione descrizione della risorsa
     * @param motore nome del motore delle regole
     *  
     */
    private record RemoteGameType(long id, String nome, String descrizione, String motore) {
    }
}
