package it.uniupo.pissir.darts.locale.unit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code ActiveMatchQueryService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
@ExtendWith(MockitoExtension.class)
class ActiveMatchQueryServiceTest {

    @Mock
    private ServiceHttpClient httpClient;
    @Mock
    private ServiceTokenClient serviceTokenClient;

    private ActiveMatchQueryService activeMatchQueryService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        lenient().when(serviceTokenClient.token()).thenReturn("test-service-token");
        activeMatchQueryService = new ActiveMatchQueryService(httpClient, "http://localhost:7004", serviceTokenClient);
    }

    /**
     * Verifica {@code activeMatches} nel caso partita servizio raggiungibile restituisce contenuto senza avviso. 
     */
    @Test
    void activeMatches_matchServiceRaggiungibile_restituisceContenutoSenzaAvviso() {
        JsonArray partite = new JsonArray();
        partite.add(new JsonObject());
        when(httpClient.get(anyString(), anyString(), any(Type.class))).thenReturn(partite);

        JsonObject result = activeMatchQueryService.activeMatches(1L);

        assertEquals(partite, result.get("contenuto"));
        assertFalse(result.get("avvisoServizioNonRaggiungibile").getAsBoolean());
    }

    /**
     * Verifica {@code activeMatches} nel caso partita servizio irraggiungibile lista vuota con avviso. 
     */
    @Test
    void activeMatches_matchServiceIrraggiungibile_listaVuotaConAvviso() {
        when(httpClient.get(anyString(), anyString(), any(Type.class))).thenThrow(new IllegalStateException("timeout"));

        JsonObject result = activeMatchQueryService.activeMatches(1L);

        assertTrue(result.get("contenuto").getAsJsonArray().isEmpty());
        assertTrue(result.get("avvisoServizioNonRaggiungibile").getAsBoolean());
    }

    /**
     * Verifica {@code activeMatches} nel caso url contiene locale identificativo e filtro stato. 
     */
    @Test
    void activeMatches_urlContieneLocaleIdEFiltroStato() {
        when(httpClient.get(anyString(), anyString(), any(Type.class))).thenReturn(new JsonArray());

        activeMatchQueryService.activeMatches(42L);

        org.mockito.Mockito.verify(httpClient).get(eq("http://localhost:7004/partite?localeId=42&stato=IN_CORSO"),
                anyString(), any(Type.class));
    }

    /**
     * Verifica {@code requireNoActiveMatch} nel caso blocca il bersaglio con partita in corso. 
     */
    @Test
    void requireNoActiveMatch_bloccaIlBersaglioConPartitaInCorso() {
        JsonObject partita = new JsonObject();
        partita.addProperty("dartboardId", 3);
        JsonArray contenuto = new JsonArray();
        contenuto.add(partita);
        JsonObject pagina = new JsonObject();
        pagina.add("contenuto", contenuto);
        when(httpClient.get(anyString(), anyString(), any(Type.class))).thenReturn(pagina);

        assertThrows(it.uniupo.pissir.darts.common.exception.ConflictException.class,
                () -> activeMatchQueryService.requireNoActiveMatch(2, 3));
    }

    /**
     * Verifica {@code requireNoActiveMatch} nel caso non libera se partita servizio non risponde. 
     */
    @Test
    void requireNoActiveMatch_nonLiberaSeMatchServiceNonRisponde() {
        when(httpClient.get(anyString(), anyString(), any(Type.class))).thenThrow(new IllegalStateException("timeout"));

        assertThrows(it.uniupo.pissir.darts.common.exception.ConflictException.class,
                () -> activeMatchQueryService.requireNoActiveMatch(2, 3));
    }
}
