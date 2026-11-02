package it.uniupo.pissir.darts.gateway.filter;

import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.gateway.config.RouteTable;
import spark.Request;

import java.util.Set;

/**
 * Controlla i token delle richieste protette in ingresso al gateway.
 *
 * @see java.util.Set
 *  
 */
public final class JwtSecurityFilter {

    public static final String ATTR_USER = "gateway.authenticatedUser";

    private static final Set<String> PATH_PUBBLICI =
            Set.of("/auth/login", "/auth/register", "/auth/jwks.json", "/health");

    private final JwtTokenService jwtTokenService;
    private final RouteTable routeTable;

    /**
     * Inizializza {@code JwtSecurityFilter} con i dati e le dipendenze indicati.
     *
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param routeTable route table da utilizzare
     * @see it.uniupo.pissir.darts.common.security.JwtTokenService
     *  
     */
    public JwtSecurityFilter(JwtTokenService jwtTokenService, RouteTable routeTable) {
        this.jwtTokenService = jwtTokenService;
        this.routeTable = routeTable;
    }

    /**
     * Indica se pubblico per i dati forniti.
     *
     * @param req richiesta HTTP ricevuta
     * @return {@code true} se pubblico per i dati forniti; {@code false} altrimenti  
     *  
     */
    public boolean isPubblico(Request req) {
        return PATH_PUBBLICI.contains(req.pathInfo()) || routeTable.risolvi(req.pathInfo()).isEmpty();
    }

    /**
     * Verifica le credenziali e i dati necessari all'operazione.
     *
     * @param req richiesta HTTP ricevuta  
     *  
     */
    public void verifica(Request req) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        req.attribute(ATTR_USER, user);
    }
}
