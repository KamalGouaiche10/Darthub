package it.uniupo.pissir.darts.gateway.integration;

import com.sun.net.httpserver.HttpServer;
import it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code HealthAggregator} con casi di prova JUnit.
 *
 * @see com.sun.net.httpserver.HttpServer
 *  
 */
class HealthAggregatorTest {

    private HttpServer servizioSano;
    private HttpServer servizioInErrore;

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        if (servizioSano != null) {
            servizioSano.stop(0);
        }
        if (servizioInErrore != null) {
            servizioInErrore.stop(0);
        }
    }

    /**
     * Verifica {@code interroga} nel caso distingue servizi raggiungibili e non raggiungibili.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void interroga_distingueServiziRaggiungibiliENonRaggiungibili() throws Exception {
        servizioSano = avviaStub(200, "{\"servizio\":\"sano\",\"database\":\"OK\"}");
        servizioInErrore = avviaStub(500, "");
        int portaChiusa = portaLiberaNonInUso();

        Map<String, String> baseUrlPerServizio = Map.of(
                "sano", "http://localhost:" + servizioSano.getAddress().getPort(),
                "in-errore", "http://localhost:" + servizioInErrore.getAddress().getPort(),
                "irraggiungibile", "http://localhost:" + portaChiusa);

        HealthAggregator aggregator = new HealthAggregator(baseUrlPerServizio, Duration.ofSeconds(10));

        List<HealthAggregator.EsitoServizio> esiti = aggregator.interroga();

        assertEquals(3, esiti.size());
        assertTrue(esitoDi(esiti, "sano").raggiungibile());
        assertFalse(esitoDi(esiti, "in-errore").raggiungibile());
        assertFalse(esitoDi(esiti, "irraggiungibile").raggiungibile());
    }

    /**
     * Avvia stub per lo stato del servizio.
     *
     * @param status codice di stato HTTP
     * @param corpo corpo del messaggio
     * @return l'oggetto {@link com.sun.net.httpserver.HttpServer} prodotto o recuperato
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @see com.sun.net.httpserver.HttpServer
     *  
     */
    private static HttpServer avviaStub(int status, String corpo) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] body = corpo.getBytes(StandardCharsets.UTF_8);
        server.createContext("/health", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    /**
     * Legge l'esito della partita dai dati di prova.
     *
     * @param esiti insieme dei dati relativi a esiti
     * @param nome nome della risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator.EsitoServizio} prodotto o recuperato
     * @see it.uniupo.pissir.darts.gateway.monitoring.HealthAggregator.EsitoServizio
     *  
     */
    private static HealthAggregator.EsitoServizio esitoDi(List<HealthAggregator.EsitoServizio> esiti, String nome) {
        return esiti.stream().filter(e -> e.nome().equals(nome)).findFirst()
                .orElseThrow(() -> new AssertionError("Nessun esito per " + nome));
    }

    /**
     * Individua una porta non occupata per la prova di connessione.
     *
     * @return il valore numerico calcolato o letto
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    private static int portaLiberaNonInUso() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
