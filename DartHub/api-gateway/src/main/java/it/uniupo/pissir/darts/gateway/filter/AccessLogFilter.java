package it.uniupo.pissir.darts.gateway.filter;

import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * Registra le richieste ricevute dal gateway. 
 */
public final class AccessLogFilter {

    private static final Logger LOG = LoggerFactory.getLogger("access");
    private static final String ATTR_START = "accessLog.startNanos";

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private AccessLogFilter() {
    }

    /**
     * Registra i dati essenziali della richiesta in ingresso.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare  
     *  
     */
    public static void before(Request req, Response res) {
        req.attribute(ATTR_START, System.nanoTime());
    }

    /**
     * Completa l'elaborazione della richiesta prima di inviare la risposta.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare  
     *  
     */
    public static void after(Request req, Response res) {
        Long start = req.attribute(ATTR_START);
        long durataMs = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
        AuthenticatedUser user = req.attribute(JwtSecurityFilter.ATTR_USER);
        String userId = user == null ? "-" : String.valueOf(user.userId());
        LOG.info("{} {} -> {} ({} ms) userId={}", req.requestMethod(), req.pathInfo(), res.status(), durataMs, userId);
    }
}
