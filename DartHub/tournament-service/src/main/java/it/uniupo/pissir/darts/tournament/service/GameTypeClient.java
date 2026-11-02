package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;

/**
 * Verifica l'esistenza di un tipo di gioco chiamando game-service con un token SERVICE dal grant client-credentials dell'Authorization Server (§14).
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class GameTypeClient {

    private final ServiceHttpClient httpClient;
    private final String gameServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

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
     * Verifica il requisito exists prima di eseguire l'operazione.
     *
     * @param gameTypeId identificativo del gioco
     *  
     */
    public void requireExists(long gameTypeId) {
        httpClient.get(gameServiceBaseUrl + "/giochi/" + gameTypeId, serviceTokenClient.token(), Object.class);
    }
}
