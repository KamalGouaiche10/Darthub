package it.uniupo.pissir.darts.gateway.unit;

import it.uniupo.pissir.darts.gateway.filter.CorsFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;
import spark.Response;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code CorsFilter} con casi di prova JUnit.
 *
 * @see spark.Request
 *  
 */
@ExtendWith(MockitoExtension.class)
class CorsFilterTest {

    @Mock
    private Request req;
    @Mock
    private Response res;

    /**
     * Verifica {@code applica} nel caso origine consentita imposta header di risposta. 
     */
    @Test
    void applica_origineConsentita_impostaHeaderDiRisposta() {
        CorsFilter filter = new CorsFilter(List.of("https://app.darthub.it"));
        when(req.headers("Origin")).thenReturn("https://app.darthub.it");
        when(req.requestMethod()).thenReturn("GET");

        filter.applica(req, res);

        verify(res).header("Access-Control-Allow-Origin", "https://app.darthub.it");
        verify(res).header("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Verifica {@code applica} nel caso origine non consentita non imposta header. 
     */
    @Test
    void applica_origineNonConsentita_nonImpostaHeader() {
        CorsFilter filter = new CorsFilter(List.of("https://app.darthub.it"));
        when(req.headers("Origin")).thenReturn("https://malintenzionato.it");
        when(req.requestMethod()).thenReturn("GET");

        filter.applica(req, res);

        verify(res, never()).header("Access-Control-Allow-Origin", "https://malintenzionato.it");
    }

    /**
     * Verifica {@code applica} nel caso wildcard consente qualunque origine. 
     */
    @Test
    void applica_wildcard_consenteQualunqueOrigine() {
        CorsFilter filter = new CorsFilter(List.of("*"));
        when(req.headers("Origin")).thenReturn("https://qualunque-sito.it");
        when(req.requestMethod()).thenReturn("GET");

        filter.applica(req, res);

        verify(res).header("Access-Control-Allow-Origin", "https://qualunque-sito.it");
    }

    /**
     * Verifica {@code applica} nel caso nessun header origin non imposta nulla. 
     */
    @Test
    void applica_nessunHeaderOrigin_nonImpostaNulla() {
        CorsFilter filter = new CorsFilter(List.of("*"));
        when(req.headers("Origin")).thenReturn(null);
        when(req.requestMethod()).thenReturn("GET");

        filter.applica(req, res);

        verify(res, never()).header(org.mockito.ArgumentMatchers.eq("Access-Control-Allow-Origin"), org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * Verifica {@code applica} nel caso preflight options risponde204 e interrompe la catena. 
     */
    @Test
    void applica_preflightOptions_risponde204EInterrompeLaCatena() {
        CorsFilter filter = new CorsFilter(List.of("*"));
        when(req.headers("Origin")).thenReturn("https://app.darthub.it");
        when(req.requestMethod()).thenReturn("OPTIONS");

        boolean interrotta = filter.applica(req, res);

        assertTrue(interrotta);
        verify(res).header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        verify(res).status(204);
    }

    /**
     * Verifica {@code applica} nel caso richiesta normale non interrompe la catena. 
     */
    @Test
    void applica_richiestaNormale_nonInterrompeLaCatena() {
        CorsFilter filter = new CorsFilter(List.of("*"));
        when(req.headers("Origin")).thenReturn("https://app.darthub.it");
        when(req.requestMethod()).thenReturn("GET");

        boolean interrotta = filter.applica(req, res);

        assertFalse(interrotta);
        verify(res, never()).status(204);
    }
}
