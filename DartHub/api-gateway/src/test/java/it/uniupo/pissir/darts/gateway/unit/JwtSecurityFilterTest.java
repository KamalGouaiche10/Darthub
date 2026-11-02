package it.uniupo.pissir.darts.gateway.unit;

import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import it.uniupo.pissir.darts.gateway.config.RouteTable;
import it.uniupo.pissir.darts.gateway.filter.JwtSecurityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code JwtSecurityFilter} con casi di prova JUnit.
 *
 * @see spark.Request
 *  
 */
@ExtendWith(MockitoExtension.class)
class JwtSecurityFilterTest {

    @Mock
    private Request req;
    @Mock
    private JwtTokenService jwtTokenService;

    private JwtSecurityFilter filter;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        RouteTable routeTable = new RouteTable(GatewayConfig.load());
        filter = new JwtSecurityFilter(jwtTokenService, routeTable);
    }

    /**
     * Verifica {@code isPubblico} nel caso rotte pubbliche esplicite vero.
     *
     * @param path percorso o indirizzo della risorsa
     *  
     */
    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/register", "/health"})
    void isPubblico_rottePubblicheEsplicite_true(String path) {
        when(req.pathInfo()).thenReturn(path);

        assertTrue(filter.isPubblico(req));
    }

    /**
     * Verifica {@code isPubblico} nel caso path non instradato vero. 
     */
    @Test
    void isPubblico_pathNonInstradato_true() {
        when(req.pathInfo()).thenReturn("/index.html");

        assertTrue(filter.isPubblico(req));
    }

    /**
     * Verifica {@code isPubblico} nel caso rotte instradate non pubbliche falso.
     *
     * @param path percorso o indirizzo della risorsa
     *  
     */
    @ParameterizedTest
    @ValueSource(strings = {"/users/5", "/partite", "/tornei/1", "/statistiche/globali", "/auth/refresh"})
    void isPubblico_rotteInstradateNonPubbliche_false(String path) {
        when(req.pathInfo()).thenReturn(path);

        assertFalse(filter.isPubblico(req));
    }
}
