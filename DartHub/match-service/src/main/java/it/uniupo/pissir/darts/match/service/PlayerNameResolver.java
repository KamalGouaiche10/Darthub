package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;

/**
 * Risolve il nome visualizzato di un giocatore per il roster inviato all'edge.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class PlayerNameResolver {

    private final ServiceHttpClient httpClient;
    private final String userServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

    /**
     * Inizializza {@code PlayerNameResolver} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param userServiceBaseUrl indirizzo di base dell'utente servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public PlayerNameResolver(ServiceHttpClient httpClient, String userServiceBaseUrl, ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.userServiceBaseUrl = userServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Recupera il nome pubblico del partecipante indicato.
     *
     * @param playerId identificativo del giocatore
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String resolve(long playerId) {
        try {
            RemoteUser user = httpClient.get(userServiceBaseUrl + "/users/" + playerId, serviceTokenClient.token(), RemoteUser.class);
            if (user.nickname() != null && !user.nickname().isBlank()) {
                return user.nickname().trim();
            }
            return user.username() == null || user.username().isBlank() ? "Giocatore #" + playerId : user.username();
        } catch (RuntimeException e) {
            return "Giocatore #" + playerId;
        }
    }

    /**
     * Raccoglie i dati di remoto utente.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del remoto utente
     * @param username nome dell'account
     * @param nickname nome pubblico del giocatore
     *  
     */
    private record RemoteUser(long id, String username, String nickname) {
    }
}
