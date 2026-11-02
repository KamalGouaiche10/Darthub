package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.tournament.service.UserClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code UserClient} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
class UserClientTest {
    private final ServiceHttpClient http = mock(ServiceHttpClient.class);
    private final ServiceTokenClient tokens = mock(ServiceTokenClient.class);
    private final UserClient client = new UserClient(http, "http://users", tokens);

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
     * Verifica il caso tornei usano nickname anche con nome e cognome. 
     */
    @Test
    void torneiUsanoNicknameAncheConNomeECognome() {
        risposta("""
                {"username":"freccia87","nickname":" Maverick ","firstName":"Marco","lastName":"Rossi"}
                """);
        assertEquals("Maverick", client.displayName(5));
    }

    /**
     * Verifica il caso nickname assente usa username. 
     */
    @Test
    void nicknameAssenteUsaUsername() {
        risposta("{\"username\":\"freccia87\",\"nickname\":\" \"}");
        assertEquals("freccia87", client.displayName(5));
    }

    /**
     * Verifica il caso dati incompleti usano segnaposto. 
     */
    @Test
    void datiIncompletiUsanoSegnaposto() {
        risposta("{}");
        assertEquals("Giocatore #5", client.displayName(5));
    }

    /**
     * Verifica il caso servizio non disponibile usa segnaposto. 
     */
    @Test
    void servizioNonDisponibileUsaSegnaposto() {
        when(tokens.token()).thenThrow(new IllegalStateException("offline"));
        assertEquals("Giocatore #5", client.displayName(5));
    }
}
