package it.uniupo.pissir.darts.gateway.filter;

import spark.Request;
import spark.Response;

import java.util.List;

/**
 * Gestisce gli header CORS per le origini configurate.
 *
 * @see java.util.List
 *  
 */
public final class CorsFilter {

    private static final int HTTP_NO_CONTENT = 204;

    private final List<String> originiConsentite;

    /**
     * Inizializza {@code CorsFilter} con i dati e le dipendenze indicati.
     *
     * @param originiConsentite insieme dei dati relativi a origini consentite
     * @see java.util.List
     *  
     */
    public CorsFilter(List<String> originiConsentite) {
        this.originiConsentite = originiConsentite;
    }

    /**
     * Applica le regole CORS e gestisce le richieste preliminari del browser.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return {@code true} se la richiesta preliminare OPTIONS è stata gestita; {@code false} altrimenti  
     *  
     */
    public boolean applica(Request req, Response res) {
        String origin = req.headers("Origin");
        if (origin != null && (originiConsentite.contains("*") || originiConsentite.contains(origin))) {
            res.header("Access-Control-Allow-Origin", origin);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Vary", "Origin");
        }
        if ("OPTIONS".equals(req.requestMethod())) {
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
            res.header("Access-Control-Max-Age", "3600");
            res.status(HTTP_NO_CONTENT);
            return true;
        }
        return false;
    }
}
