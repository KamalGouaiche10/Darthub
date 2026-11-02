package it.uniupo.pissir.darts.edge.mqtt;

import it.uniupo.pissir.darts.edge.config.EdgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica il comportamento di {@code EdgeRuntimeConfiguration} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class EdgeRuntimeConfigurationTest {
    @TempDir Path temp;
    /**
     * Verifica il caso rifiuta parametri invalidi prima di applicarli. 
     */
    @Test void rifiutaParametriInvalidiPrimaDiApplicarli() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeConfigSubscriber.Configuration(0, 20, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new EdgeConfigSubscriber.Configuration(10, 5, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new EdgeConfigSubscriber.Configuration(10, 20, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new EdgeConfigSubscriber.Configuration(10, 20, 1, "https://localhost"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeConfigSubscriber.Configuration(10, 20, 1, "tcp://user:secret@localhost:1883"));
    }
    /**
     * Verifica il caso configurazione ricevuta sopravvive al riavvio senza modificare il file iniziale.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test void configurazioneRicevutaSopravviveAlRiavvioSenzaModificareIlFileIniziale() throws Exception {
        Path file = temp.resolve("initial.properties");
        String original = "edge.pathDatabase=" + temp.resolve("edge.db").toString().replace('\\','/') + "\nedge.intervalloHeartbeatSecondi=30\n";
        Files.writeString(file, original);
        String previous = System.getProperty("config.file");
        try {
            System.setProperty("config.file", file.toString());
            EdgeProperties properties = EdgeProperties.load();
            properties.salvaConfigurazione(2, 10, 1, "tcp://localhost:1884");
            EdgeProperties reloaded = EdgeProperties.load();
            assertEquals(2, reloaded.intervalloHeartbeatSecondi());
            assertEquals(1, reloaded.mqttQos());
            assertEquals("tcp://localhost:1884", reloaded.brokerLocale());
            assertEquals(original, Files.readString(file));
        } finally {
            if (previous == null) System.clearProperty("config.file"); else System.setProperty("config.file", previous);
        }
    }
}
