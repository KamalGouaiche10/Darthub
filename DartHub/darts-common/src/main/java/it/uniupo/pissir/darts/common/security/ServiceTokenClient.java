package it.uniupo.pissir.darts.common.security;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Ottiene e conserva temporaneamente il token delle chiamate fra servizi.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class ServiceTokenClient {

    private static final long MARGINE_RINNOVO_SECONDI = 30;

    private final ServiceHttpClient httpClient;
    private final String userServiceBaseUrl;
    private final String clientId;
    private final String clientSecret;

    private volatile String tokenInCache;
    private volatile Instant scadenza = Instant.MIN;

    /**
     * Inizializza {@code ServiceTokenClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param userServiceBaseUrl indirizzo di base dell'utente servizio
     * @param clientId identificativo del client MQTT
     * @param clientSecret credenziale del client tecnico
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public ServiceTokenClient(ServiceHttpClient httpClient, String userServiceBaseUrl, String clientId, String clientSecret) {
        this.httpClient = httpClient;
        this.userServiceBaseUrl = userServiceBaseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Restituisce il componente {@code String} utilizzato dall'operazione.
     *
     * @return token da utilizzare
     *  
     */
    public synchronized String token() {
        if (tokenInCache == null || Instant.now().isAfter(scadenza)) {
            rinnova();
        }
        return tokenInCache;
    }

    /**
     * Richiede un nuovo token per le chiamate fra servizi. 
     */
    private void rinnova() {
        Map<String, String> body = Map.of("clientId", clientId, "clientSecret", clientSecret);
        ServiceTokenResponse risposta = httpClient.post(userServiceBaseUrl + "/auth/service-token", body, null,
                ServiceTokenResponse.class);
        tokenInCache = risposta.accessToken();
        scadenza = Instant.now().plus(Duration.ofSeconds(Math.max(0, risposta.expiresInSeconds() - MARGINE_RINNOVO_SECONDI)));
    }

    /**
     * Raccoglie i dati della risposta relativa a servizio token.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param accessToken token JWT di accesso alla sessione
     * @param tokenType tipo del token restituito
     * @param expiresInSeconds durata residua del token in secondi
     *  
     */
    private record ServiceTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }
}
