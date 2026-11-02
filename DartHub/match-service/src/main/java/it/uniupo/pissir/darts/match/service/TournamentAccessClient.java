package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;

/**
 * Interroga il servizio remoto per recuperare tornei.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class TournamentAccessClient {
    private final ServiceHttpClient http;
    private final String baseUrl;
    /**
     * Inizializza {@code TournamentAccessClient} con i dati e le dipendenze indicati.
     *
     * @param http HTTP da utilizzare
     * @param baseUrl indirizzo di base del servizio
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public TournamentAccessClient(ServiceHttpClient http, String baseUrl) {
        this.http = http; this.baseUrl = baseUrl;
    }
    /**
     * Verifica l'accesso dell'utente alla risorsa.
     *
     * @param matchId identificativo della partita
     * @param authorizationHeader header HTTP di autorizzazione
     *  
     */
    public void requireAccess(long matchId, String authorizationHeader) {
        request(matchId, authorizationHeader, "accesso");
    }
    /**
     * Richiede che il partecipante esista e sia accessibile.
     *
     * @param matchId identificativo della partita
     * @param authorizationHeader header HTTP di autorizzazione
     *  
     */
    public void requireParticipant(long matchId, String authorizationHeader) {
        request(matchId, authorizationHeader, "appuntamento");
    }
    /**
     * Esegue la richiesta HTTP con i parametri e le credenziali indicati.
     *
     * @param matchId identificativo della partita
     * @param authorizationHeader header HTTP di autorizzazione
     * @param endpoint percorso dell'endpoint HTTP
     *  
     */
    private void request(long matchId, String authorizationHeader, String endpoint) {
        String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7) : null;
        http.get(baseUrl + "/tornei/partite/" + matchId + "/" + endpoint, token, Object.class);
    }
}
