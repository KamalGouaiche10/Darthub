package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.match.service.PlayerNameResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code PlayerNameResolver} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
class PlayerNameResolverTest {
    private final ServiceHttpClient http = mock(ServiceHttpClient.class);
    private final ServiceTokenClient tokens = mock(ServiceTokenClient.class);
    private final PlayerNameResolver resolver = new PlayerNameResolver(http, "http://users", tokens);

    /**
     * Prepara i dati di void per il caso di prova.
     *
     * @param json testo JSON da deserializzare
     *  
     */
    private void risposta(String json) {
        when(tokens.token()).thenReturn("service-token");
        when(http.get(anyString(), anyString(), any())).thenAnswer(call ->
                JsonSupport.fromJson(json, call.<Class<?>>getArgument(2)));
    }

    /**
     * Verifica il caso nickname ha precedenza su nome cognome e username. 
     */
    @Test
    void nicknameHaPrecedenzaSuNomeCognomeEUsername() {
        risposta("""
                {"id":5,"username":"freccia87","nickname":" Maverick ","firstName":"Marco","lastName":"Rossi"}
                """);
        assertEquals("Maverick", resolver.resolve(5));
    }

    /**
     * Verifica il caso nickname assente usa username senza esporre nome cognome. 
     */
    @Test
    void nicknameAssenteUsaUsernameSenzaEsporreNomeCognome() {
        risposta("""
                {"id":5,"username":"freccia87","nickname":" ","firstName":"Marco","lastName":"Rossi"}
                """);
        assertEquals("freccia87", resolver.resolve(5));
    }

    /**
     * Verifica il caso identita incompleta usa segnaposto. 
     */
    @Test
    void identitaIncompletaUsaSegnaposto() {
        risposta("{\"id\":5}");
        assertEquals("Giocatore #5", resolver.resolve(5));
    }

    /**
     * Verifica il caso servizio non disponibile usa segnaposto. 
     */
    @Test
    void servizioNonDisponibileUsaSegnaposto() {
        when(tokens.token()).thenThrow(new IllegalStateException("offline"));
        assertEquals("Giocatore #5", resolver.resolve(5));
    }
}
