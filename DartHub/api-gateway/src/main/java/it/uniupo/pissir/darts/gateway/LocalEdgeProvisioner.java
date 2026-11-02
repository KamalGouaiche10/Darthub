package it.uniupo.pissir.darts.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Configura e avvia i dispositivi edge della demo locale.
 *
 * @see java.nio.file.Path
 *  
 */
public final class LocalEdgeProvisioner {
    private final Path root;

    /**
     * Inizializza {@code LocalEdgeProvisioner} con i dati e le dipendenze indicati.
     *
     * @param root cartella radice del progetto
     * @see java.nio.file.Path
     *  
     */
    public LocalEdgeProvisioner(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Rimuove il locale edge provisioner verificando i vincoli dell'operazione.
     *
     * @param deviceId identificativo del dispositivo
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    public synchronized void remove(long deviceId) throws IOException {
        remove(deviceId, -1, -1);
    }

    /**
     * Ferma l'attività del componente.
     *
     * @param deviceId identificativo del dispositivo
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @throws IllegalArgumentException se viene rilevato l'errore «ID dispositivo non valido»
     *  
     */
    public synchronized void stop(long deviceId, long localeId, long dartboardId) throws IOException {
        if (deviceId <= 0) throw new IllegalArgumentException("ID dispositivo non valido");
        Path configPath = root.resolve("data/edge-instances/edge-" + deviceId + ".properties");
        if (Files.exists(configPath)) {
            stopManagedProcess("edge-component-auto-" + deviceId, "edge-" + deviceId + ".properties");
        }
        if (localeId > 0 && dartboardId > 0) {
            Path legacyConfig = root.resolve("edge-component/edge-" + localeId + "-" + dartboardId + ".properties");
            if (Files.exists(legacyConfig)) {
                stopManagedProcess("edge-component-" + localeId + "-" + dartboardId,
                        legacyConfig.getFileName().toString());
            }
            if (localeId == 1 && dartboardId == 1) {
                stopManagedProcess("edge-component", null);
            }
        }
    }

    /**
     * Rimuove il locale edge provisioner verificando i vincoli dell'operazione.
     *
     * @param deviceId identificativo del dispositivo
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    public synchronized void remove(long deviceId, long localeId, long dartboardId) throws IOException {
        stop(deviceId, localeId, dartboardId);
        Path configPath = root.resolve("data/edge-instances/edge-" + deviceId + ".properties");
        Files.deleteIfExists(root.resolve("data/edge-device-" + deviceId + ".secret"));
        Files.deleteIfExists(configPath);
        Files.deleteIfExists(root.resolve("logs/pid/edge-component-auto-" + deviceId + ".pid"));
        if (localeId > 0 && dartboardId > 0) {
            Path legacyConfig = root.resolve("edge-component/edge-" + localeId + "-" + dartboardId + ".properties");
            if (Files.exists(legacyConfig)) {
                Properties properties = new Properties();
                try (var in = Files.newInputStream(legacyConfig)) {
                    properties.load(in);
                }
                if (Long.toString(localeId).equals(properties.getProperty("edge.localeId")) &&
                        Long.toString(dartboardId).equals(properties.getProperty("edge.dartboardId"))) {
                    Files.deleteIfExists(root.resolve("data/edge-" + localeId + "-" + dartboardId + "-token.secret"));
                    Files.deleteIfExists(root.resolve("logs/pid/edge-component-" + localeId + "-" + dartboardId + ".pid"));
                }
            }
            if (localeId == 1 && dartboardId == 1) {
                Files.deleteIfExists(root.resolve("data/edge-token.secret"));
                Files.deleteIfExists(root.resolve("logs/pid/edge-component.pid"));
            }
        }
    }

    /**
     * Ferma il processo registrato dopo averne verificato il comando.
     *
     * @param name nome del componente o della risorsa
     * @param configMarker nome della configurazione attesa nel comando del processo
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    private void stopManagedProcess(String name, String configMarker) throws IOException {
        Path pidPath = root.resolve("logs/pid/" + name + ".pid");
        if (Files.exists(pidPath)) {
            long pid;
            try {
                pid = Long.parseLong(Files.readString(pidPath, StandardCharsets.UTF_8).trim());
            } catch (NumberFormatException e) {
                throw new IOException("PID edge non valido in " + pidPath, e);
            }
            ProcessHandle process = ProcessHandle.of(pid).orElse(null);
            if (process != null && process.isAlive()) {
                String command = commandLine(process).replace('\\', '/');
                if (!command.contains("edge-component/target/edge-component.jar") ||
                        (configMarker != null && !command.contains(configMarker)) ||
                        (configMarker == null && command.contains("-Dconfig.file="))) {
                    throw new IOException("PID edge non corrispondente: arresto manuale necessario");
                }
                process.destroy();
                try {
                    process.onExit().get(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IOException("Impossibile arrestare l'edge " + name, e);
                }
            }
        }
    }

    /**
     * Legge il comando con cui è stato avviato il processo.
     *
     * @param process processo da utilizzare
     * @return il comando completo associato al processo
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    private String commandLine(ProcessHandle process) throws IOException {
        String command = process.info().commandLine().orElse("");
        if (!System.getProperty("os.name").startsWith("Windows")) return command;
        if (command.contains("edge-component.jar") && command.contains("-Dconfig.file=")) return command;
        String[] arguments = process.info().arguments().orElse(new String[0]);
        String withArguments = process.info().command().orElse("") + " " + String.join(" ", arguments);
        if (withArguments.contains("edge-component.jar") && withArguments.contains("-Dconfig.file="))
            return withArguments;
        Process query = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-CimInstance Win32_Process -Filter 'ProcessId = " + process.pid() + "').CommandLine")
                .redirectErrorStream(true).start();
        try {
            if (!query.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                query.destroyForcibly();
                throw new IOException("Controllo del PID edge scaduto");
            }
            if (query.exitValue() != 0) throw new IOException("Controllo del PID edge fallito");
            return new String(query.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Controllo del PID edge interrotto", e);
        }
    }

    /**
     * Scrive la configurazione locale e avvia il dispositivo edge.
     *
     * @param deviceId identificativo del dispositivo
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param gameTypeId identificativo del gioco
     * @param serialNumber numero seriale del dispositivo
     * @param token token da utilizzare
     * @param heartbeatSeconds intervallo dei segnali di presenza in secondi
     * @param localBroker indirizzo del broker MQTT del sensore
     * @return il valore numerico calcolato o letto
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     * @throws IllegalArgumentException se viene rilevato l'errore «Dati del dispositivo edge non validi»
     *  
     */
    public synchronized int provision(long deviceId, long localeId, long dartboardId, long gameTypeId,
                                      String serialNumber, String token, int heartbeatSeconds,
                                      String localBroker) throws IOException, InterruptedException {
        if (deviceId <= 0 || localeId <= 0 || dartboardId <= 0 || gameTypeId <= 0 ||
                serialNumber == null || serialNumber.isBlank() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Dati del dispositivo edge non validi");
        }
        Path jar = root.resolve("edge-component/target/edge-component.jar");
        if (!Files.isRegularFile(jar)) {
            throw new IOException("JAR edge non trovato: esegui mvn package");
        }
        Path configDir = root.resolve("data/edge-instances");
        Path logDir = root.resolve("logs");
        Path pidDir = logDir.resolve("pid");
        Files.createDirectories(configDir);
        Files.createDirectories(pidDir);
        Path configPath = configDir.resolve("edge-" + deviceId + ".properties");
        Path tokenPath = root.resolve("data/edge-device-" + deviceId + ".secret");
        if (Files.exists(configPath) || Files.exists(tokenPath)) {
            throw new IOException("Configurazione edge gia presente per il dispositivo " + deviceId);
        }
        int port = availablePort(configDir);
        Files.writeString(tokenPath, token.trim(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Properties config = new Properties();
        config.setProperty("edge.localeId", Long.toString(localeId));
        config.setProperty("edge.dartboardId", Long.toString(dartboardId));
        config.setProperty("edge.gameTypeId", Long.toString(gameTypeId));
        config.setProperty("edge.serialNumber", serialNumber);
        config.setProperty("edge.pathDatabase", "data/edge-device-" + deviceId + ".db");
        config.setProperty("edge.pathToken", "data/edge-device-" + deviceId + ".secret");
        config.setProperty("edge.portaDisplay", Integer.toString(port));
        config.setProperty("mqtt.brokerLocale", localBroker == null || localBroker.isBlank()
                ? "tcp://localhost:1883" : localBroker);
        config.setProperty("mqtt.brokerCentrale", "tcp://localhost:1883");
        config.setProperty("edge.gatewayUrl", "https://localhost:8443");
        config.setProperty("edge.localeServiceUrl", "http://localhost:7002");
        config.setProperty("edge.userServiceUrl", "http://localhost:7001");
        config.setProperty("edge.intervalloHeartbeatSecondi", Integer.toString(heartbeatSeconds));
        config.setProperty("edge.syncRetryBaseSecondi", "5");
        config.setProperty("edge.syncRetryMaxSecondi", "300");
        config.setProperty("database.poolSize", "4");
        try (var out = Files.newOutputStream(configPath, StandardOpenOption.CREATE_NEW)) {
            config.store(out, "DartHub local edge " + deviceId);
        }

        String name = "edge-component-auto-" + deviceId;
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.toString(), "-Dconfig.file=" + configPath,
                "-jar", jar.toString())
                .directory(root.toFile())
                .redirectOutput(logDir.resolve(name + ".out.log").toFile())
                .redirectError(logDir.resolve(name + ".err.log").toFile())
                .start();
        Files.writeString(pidDir.resolve(name + ".pid"), Long.toString(process.pid()), StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 60; attempt++) {
            if (!process.isAlive()) {
                throw new IOException("L'edge si e fermato: controlla logs/" + name + ".err.log");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 200);
                return port;
            } catch (IOException ignored) {
                Thread.sleep(500);
            }
        }
        throw new IOException("L'edge non risponde sulla porta " + port + ": controlla i log");
    }

    /**
     * Seleziona una porta libera per il nuovo dispositivo edge.
     *
     * @param configDir cartella delle configurazioni locali
     * @return una porta TCP libera nell'intervallo previsto per gli edge locali
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     * @see java.nio.file.Path
     *  
     */
    private int availablePort(Path configDir) throws IOException {
        Set<Integer> assigned = new HashSet<>();
        try (var files = Files.list(configDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("edge-[0-9]+\\.properties")).toList()) {
                Properties config = new Properties();
                try (var in = Files.newInputStream(file)) {
                    config.load(in);
                }
                assigned.add(Integer.parseInt(config.getProperty("edge.portaDisplay")));
            }
        }
        for (int port = 7300; port < 7400; port++) {
            if (assigned.contains(port)) continue;
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
                return port;
            } catch (IOException ignored) {
            }
        }
        throw new IOException("Nessuna porta libera per un nuovo dispositivo edge");
    }
}
