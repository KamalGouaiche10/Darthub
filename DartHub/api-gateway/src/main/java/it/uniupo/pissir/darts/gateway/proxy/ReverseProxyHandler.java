package it.uniupo.pissir.darts.gateway.proxy;

import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.gateway.filter.JwtSecurityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Inoltra le richieste del gateway al servizio destinatario. 
 */
public final class ReverseProxyHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ReverseProxyHandler.class);

    private static final Set<String> HEADER_DA_ESCLUDERE = Set.of("connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");

    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Inizializza {@code ReverseProxyHandler} con i dati e le dipendenze indicati.
     *
     * @param timeout tempo massimo di attesa
     * @see java.time.Duration
     *  
     */
    public ReverseProxyHandler(Duration timeout) {
        this.httpClient = HttpClient.newHttpClient();
        this.timeout = timeout;
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @param baseUrl indirizzo di base del servizio
     * @return l'oggetto {@code Object} prodotto o recuperato  
     *  
     */
    public Object inoltra(Request req, Response res, String baseUrl) {
        String query = req.queryString();
        String url = baseUrl + req.pathInfo() + (query == null || query.isBlank() ? "" : "?" + query);

        HttpRequest.BodyPublisher bodyPublisher = corpoRichiesta(req);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .method(req.requestMethod(), bodyPublisher);
        for (String nome : req.headers()) {
            if (!HEADER_DA_ESCLUDERE.contains(nome.toLowerCase())) {
                try {
                    builder.header(nome, req.headers(nome));
                } catch (IllegalArgumentException e) {

                }
            }
        }
        AuthenticatedUser user = req.attribute(JwtSecurityFilter.ATTR_USER);
        if (user != null) {
            builder.header("X-User-Id", String.valueOf(user.userId()));
            builder.header("X-User-Role", user.role());
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            res.status(response.statusCode());
            response.headers().map().forEach((nome, valori) -> {
                if (!HEADER_DA_ESCLUDERE.contains(nome.toLowerCase())) {
                    valori.forEach(valore -> res.header(nome, valore));
                }
            });
            res.raw().getOutputStream().write(response.body());
            res.raw().getOutputStream().flush();
            return "";
        } catch (IOException e) {
            LOG.warn("Servizio non raggiungibile: {} — {}", url, e.getMessage());
            throw new ServiceUnavailableException("Il servizio richiesto non è al momento raggiungibile");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Chiamata al servizio interrotta");
        }
    }

    /**
     * Legge e deserializza il corpo JSON della richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @return l'oggetto {@code HttpRequest.BodyPublisher} prodotto o recuperato  
     *  
     */
    private HttpRequest.BodyPublisher corpoRichiesta(Request req) {
        byte[] corpo = req.bodyAsBytes();
        if (corpo == null || corpo.length == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(corpo);
    }
}
