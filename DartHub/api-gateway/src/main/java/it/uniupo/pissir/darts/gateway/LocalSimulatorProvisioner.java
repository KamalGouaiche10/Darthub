package it.uniupo.pissir.darts.gateway;

import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.json.JsonSupport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Avvia su richiesta il simulatore di un bersaglio e riusa le istanze già disponibili.
 *
 * @see java.nio.file.Path
 *  
 */
public final class LocalSimulatorProvisioner {
    private final Path root;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    /**
     * Inizializza {@code LocalSimulatorProvisioner} con i dati e le dipendenze indicati.
     *
     * @param root cartella radice del progetto
     * @see java.nio.file.Path
     *  
     */
    public LocalSimulatorProvisioner(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Calcola la porta assegnata al simulatore del bersaglio.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return la porta HTTP assegnata al simulatore del bersaglio
     * @throws IllegalArgumentException se viene rilevato l'errore «Bersaglio non valido»
     *  
     */
    public static int portFor(long localeId, long dartboardId) {
        if (localeId <= 0 || dartboardId <= 0) throw new IllegalArgumentException("Bersaglio non valido");
        if (localeId == 1 && dartboardId == 1) return 7200;
        if (localeId == 5 && dartboardId == 7) return 7207;
        if (localeId > 583 || dartboardId > 58335) {
            throw new IllegalArgumentException("Nessuna porta disponibile per questo bersaglio");
        }
        long port = 7200 + localeId * 100 + dartboardId;
        if (port > 65535) throw new IllegalArgumentException("Nessuna porta disponibile per questo bersaglio");
        return (int) port;
    }

    /**
     * Recupera l'istanza del simulatore oppure ne avvia una nuova.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return il testo o il valore di configurazione richiesto
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     *  
     */
    public synchronized String ensure(long localeId, long dartboardId) throws IOException, InterruptedException {
        int port = portFor(localeId, dartboardId);
        String url = "http://127.0.0.1:" + port;
        if (matches(url, localeId, dartboardId)) return url;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        } catch (IOException e) {
            throw new IOException("La porta " + port + " è occupata da un servizio diverso dal simulatore richiesto", e);
        }

        Configuration configuration = configurationFor(localeId, dartboardId);
        Path jar = root.resolve("sensor-simulator/target/sensor-simulator.jar");
        if (!Files.isRegularFile(jar)) throw new IOException("JAR del simulatore mancante: esegui mvn package");
        Path logDir = root.resolve("logs");
        Path pidDir = logDir.resolve("pid");
        Files.createDirectories(pidDir);
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        String name = "sensor-simulator-auto-" + configuration.deviceId();
        Process process = new ProcessBuilder(java.toString(), "-jar", jar.toString(),
                "--localeId", Long.toString(localeId), "--dartboardId", Long.toString(dartboardId),
                "--portaWeb", Integer.toString(port), "--broker", configuration.broker())
                .directory(root.toFile())
                .redirectOutput(logDir.resolve(name + ".out.log").toFile())
                .redirectError(logDir.resolve(name + ".err.log").toFile())
                .start();
        Path pidPath = pidDir.resolve(name + ".pid");
        boolean ready = false;
        try {
            Files.writeString(pidPath, Long.toString(process.pid()), StandardCharsets.UTF_8);
            for (int attempt = 0; attempt < 60; attempt++) {
                if (!process.isAlive()) throw new IOException("Il simulatore si è fermato: controlla logs/" + name + ".err.log");
                if (matches(url, localeId, dartboardId)) {
                    ready = true;
                    return url;
                }
                Thread.sleep(250);
            }
            throw new IOException("Il simulatore non risponde: controlla logs/" + name + ".err.log");
        } finally {
            if (!ready) {
                process.destroy();
                Files.deleteIfExists(pidPath);
            }
        }
    }

    /**
     * Verifica che il sensore raggiunto appartenga al locale e al bersaglio richiesti.
     *
     * @param url indirizzo della risorsa HTTP
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return l'esito della condizione verificata
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     *  
     */
    private boolean matches(String url, long localeId, long dartboardId) throws InterruptedException {
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url + "/simulatore/stato"))
                    .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            JsonObject state = JsonSupport.fromJson(response.body(), JsonObject.class);
            return state != null && state.get("localeId").getAsLong() == localeId
                    && state.get("dartboardId").getAsLong() == dartboardId;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Recupera la configurazione edge associata al bersaglio.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return l'oggetto {@link it.uniupo.pissir.darts.gateway.LocalSimulatorProvisioner.Configuration} prodotto o recuperato
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @see it.uniupo.pissir.darts.gateway.LocalSimulatorProvisioner.Configuration
     *  
     */
    private Configuration configurationFor(long localeId, long dartboardId) throws IOException {
        Path configDir = root.resolve("data/edge-instances");
        if (Files.isDirectory(configDir)) {
            try (var files = Files.list(configDir)) {
                for (Path file : files.filter(path -> path.getFileName().toString().matches("edge-[0-9]+\\.properties")).toList()) {
                    Properties config = new Properties();
                    try (var in = Files.newInputStream(file)) { config.load(in); }
                    if (!Long.toString(localeId).equals(config.getProperty("edge.localeId"))
                            || !Long.toString(dartboardId).equals(config.getProperty("edge.dartboardId"))) continue;
                    String deviceId = file.getFileName().toString().replace("edge-", "").replace(".properties", "");
                    if (!Files.isRegularFile(root.resolve("data/edge-device-" + deviceId + ".secret"))) continue;
                    return new Configuration(deviceId, config.getProperty("mqtt.brokerLocale", "tcp://localhost:1883"));
                }
            }
        }
        throw new IOException("Questo bersaglio non ha un edge configurato sulla demo locale. Registra e avvia l'edge oppure usa Cambia URL per un simulatore esterno.");
    }

    /**
     * Raccoglie i dati necessari ad avviare il simulatore di un bersaglio.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param deviceId identificativo del dispositivo
     * @param broker indirizzo del broker MQTT
     *  
     */
    private record Configuration(String deviceId, String broker) { }
}
