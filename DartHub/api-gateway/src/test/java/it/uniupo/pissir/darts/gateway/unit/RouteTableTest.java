package it.uniupo.pissir.darts.gateway.unit;

import it.uniupo.pissir.darts.gateway.config.GatewayConfig;
import it.uniupo.pissir.darts.gateway.config.RouteTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code RouteTable} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.gateway.config.RouteTable
 *  
 */
class RouteTableTest {

    private RouteTable routeTable;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        routeTable = new RouteTable(GatewayConfig.load());
    }

    /**
     * Verifica {@code risolvi} nel caso prefisso noto restituisce la base url del servizio sulla porta attesa.
     *
     * @param path percorso o indirizzo della risorsa
     * @param portaAttesa porta attesa da utilizzare
     *  
     */
    @ParameterizedTest
    @CsvSource({
            "/auth/login, 7001",
            "/users/5, 7001",
            "/locali/1, 7002",
            "/bersagli/1, 7002",
            "/edge-devices/register, 7002",
            "/giochi, 7003",
            "/sensori/1, 7003",
            "/regole/1, 7003",
            "/partite/1, 7004",
            "/squadre, 7004",
            "/tornei/1/calendario, 7005",
            "/statistiche/globali, 7006"
    })
    void risolvi_prefissoNoto_restituisceLaBaseUrlDelServizioSullaPortaAttesa(String path, String portaAttesa) {
        Optional<String> risolto = routeTable.risolvi(path);

        assertTrue(risolto.isPresent());
        assertTrue(risolto.get().endsWith(":" + portaAttesa));
    }

    /**
     * Verifica {@code risolvi} nel caso prefisso sconosciuto vuoto. 
     */
    @Test
    void risolvi_prefissoSconosciuto_vuoto() {
        assertTrue(routeTable.risolvi("/index.html").isEmpty());
    }

    /**
     * Verifica {@code risolvi} nel caso radice vuoto. 
     */
    @Test
    void risolvi_radice_vuoto() {
        assertTrue(routeTable.risolvi("/").isEmpty());
    }

    /**
     * Verifica {@code risolvi} nel caso stesso prefisso stessa base url. 
     */
    @Test
    void risolvi_stessoPrefisso_stessaBaseUrl() {
        assertEquals(routeTable.risolvi("/users/5"), routeTable.risolvi("/auth/login"));
    }
}
