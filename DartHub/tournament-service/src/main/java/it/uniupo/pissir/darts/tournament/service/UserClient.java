package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;

/**
 * Verifica l'esistenza di un giocatore chiamando user-service con un token SERVICE dal grant client-credentials dell'Authorization Server (ServiceTokenClient, §14) — solo user-service può firmare JWT.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class UserClient {

    private final ServiceHttpClient httpClient;
    private final String userServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

    /**
     * Inizializza {@code UserClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param userServiceBaseUrl indirizzo di base dell'utente servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public UserClient(ServiceHttpClient httpClient, String userServiceBaseUrl, ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.userServiceBaseUrl = userServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Verifica il requisito exists prima di eseguire l'operazione.
     *
     * @param playerId identificativo del giocatore
     *  
     */
    public void requireExists(long playerId) {
        httpClient.get(userServiceBaseUrl + "/users/" + playerId, serviceTokenClient.token(), Object.class);
    }

    /**
     * Recupera il nome da mostrare per il partecipante.
     *
     * @param playerId identificativo del giocatore
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String displayName(long playerId) {
        try {
            RemoteUser user = httpClient.get(userServiceBaseUrl + "/users/" + playerId,
                    serviceTokenClient.token(), RemoteUser.class);
            if (user.nickname() != null && !user.nickname().isBlank()) return user.nickname().trim();
            if (user.username() != null && !user.username().isBlank()) return user.username();
        } catch (RuntimeException ignored) { }
        return "Giocatore #" + playerId;
    }

    /**
     * Raccoglie i dati di remoto utente.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param username nome dell'account
     * @param nickname nome pubblico del giocatore
     *  
     */
    private record RemoteUser(String username, String nickname) { }
}
