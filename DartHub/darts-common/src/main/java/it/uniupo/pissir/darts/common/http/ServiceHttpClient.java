package it.uniupo.pissir.darts.common.http;

import com.google.gson.Gson;
import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.GsonFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Esegue le chiamate HTTP fra servizi con autorizzazione e gestione degli errori.
 *
 * @see java.net.http.HttpClient
 *  
 */
public final class ServiceHttpClient {

    private final HttpClient httpClient;
    private final Gson gson;
    private final Duration timeout;

    /**
     * Inizializza {@code ServiceHttpClient} con i dati e le dipendenze indicati.
     *
     * @param timeout tempo massimo di attesa
     * @see java.time.Duration
     *  
     */
    public ServiceHttpClient(Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.gson = GsonFactory.instance();
        this.timeout = timeout;
    }

    /**
     * Recupera  usando i parametri forniti.
     *
     * @param <T> tipo degli elementi gestiti
     * @param url indirizzo della risorsa HTTP
     * @param bearerToken token Bearer della chiamata
     * @param responseType risposta tipo da utilizzare
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.lang.reflect.Type
     *  
     */
    public <T> T get(String url, String bearerToken, Type responseType) {
        return send(baseRequest(url, bearerToken).GET(), responseType);
    }

    /**
     * Esegue una richiesta HTTP POST verso il percorso indicato.
     *
     * @param <T> tipo degli elementi gestiti
     * @param url indirizzo della risorsa HTTP
     * @param body corpo della richiesta o della risposta
     * @param bearerToken token Bearer della chiamata
     * @param responseType risposta tipo da utilizzare
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.lang.reflect.Type
     *  
     */
    public <T> T post(String url, Object body, String bearerToken, Type responseType) {
        return send(baseRequest(url, bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))), responseType);
    }

    /**
     * Esegue una richiesta HTTP PATCH verso il percorso indicato.
     *
     * @param <T> tipo degli elementi gestiti
     * @param url indirizzo della risorsa HTTP
     * @param body corpo della richiesta o della risposta
     * @param bearerToken token Bearer della chiamata
     * @param responseType risposta tipo da utilizzare
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.lang.reflect.Type
     *  
     */
    public <T> T patch(String url, Object body, String bearerToken, Type responseType) {
        return send(baseRequest(url, bearerToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body))), responseType);
    }

    /**
     * Rimuove il servizio HTTP verificando i vincoli dell'operazione.
     *
     * @param url indirizzo della risorsa HTTP
     * @param bearerToken token Bearer della chiamata
     *  
     */
    public void delete(String url, String bearerToken) {
        send(baseRequest(url, bearerToken).DELETE(), null);
    }

    /**
     * Prepara una richiesta HTTP con i parametri comuni.
     *
     * @param url indirizzo della risorsa HTTP
     * @param bearerToken token Bearer della chiamata
     * @return l'oggetto {@code HttpRequest.Builder} prodotto o recuperato
     *  
     */
    private HttpRequest.Builder baseRequest(String url, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json");
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return builder;
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param <T> tipo degli elementi gestiti
     * @param builder costruttore della richiesta o dell'oggetto
     * @param responseType risposta tipo da utilizzare
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.lang.reflect.Type
     *  
     */
    private <T> T send(HttpRequest.Builder builder, Type responseType) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 400) {
                throw translateError(status, response.body());
            }
            if (responseType == null || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return gson.fromJson(response.body(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Chiamata HTTP fallita", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chiamata HTTP interrotta", e);
        }
    }

    /**
     * Converte la risposta di errore del servizio in un'eccezione applicativa.
     *
     * @param status codice di stato HTTP
     * @param body corpo della richiesta o della risposta
     * @return l'oggetto {@code RuntimeException} prodotto o recuperato
     *  
     */
    private RuntimeException translateError(int status, String body) {
        String messaggio = body != null && !body.isBlank() ? body : "Errore dal servizio remoto";
        return switch (status) {
            case 400 -> new ValidationException(messaggio);
            case 401 -> new UnauthorizedException(messaggio);
            case 403 -> new ForbiddenException(messaggio);
            case 404 -> new NotFoundException(messaggio);
            case 409 -> new ConflictException(messaggio);
            case 422 -> new BusinessRuleException(messaggio);
            default -> new IllegalStateException("Errore HTTP " + status + ": " + messaggio);
        };
    }
}
