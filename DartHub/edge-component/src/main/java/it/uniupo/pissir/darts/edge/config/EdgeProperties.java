package it.uniupo.pissir.darts.edge.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Carica e rende disponibili i parametri di configurazione del componente.
 *
 * @see java.util.Properties
 *  
 */
public final class EdgeProperties {

    private final Properties properties;

    /**
     * Inizializza {@code EdgeProperties} con i dati e le dipendenze indicati.
     *
     * @param properties proprietà di configurazione
     * @see java.util.Properties
     *  
     */
    private EdgeProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * Carica il dispositivo edge dalla sorgente configurata.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.config.EdgeProperties} prodotto o recuperato
     *  
     */
    public static EdgeProperties load() {
        Properties properties = new Properties();
        String overridePath = System.getProperty("config.file", "edge.properties");
        try (InputStream in = Files.exists(Path.of(overridePath))
                ? Files.newInputStream(Path.of(overridePath))
                : EdgeProperties.class.getClassLoader().getResourceAsStream("edge.properties")) {
            if (in == null) {
                throw new IllegalStateException(overridePath + " non trovato (né su disco né nel classpath)");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Lettura della configurazione edge fallita", e);
        }
        Path runtime = runtimePath(properties);
        if (Files.exists(runtime)) {
            Properties saved = new Properties();
            try (InputStream in = Files.newInputStream(runtime)) { saved.load(in); }
            catch (IOException e) { throw new IllegalStateException("Lettura configurazione ricevuta fallita", e); }
            for (String key : java.util.List.of("edge.intervalloHeartbeatSecondi", "edge.sogliaOfflineSecondi", "mqtt.qos", "mqtt.brokerLocale")) {
                if (saved.containsKey(key)) properties.setProperty(key, saved.getProperty(key));
            }
        }
        return new EdgeProperties(properties);
    }

    /**
     * Risolve il percorso di un file usato durante l'esecuzione.
     *
     * @param p dato {@link java.util.Properties} da elaborare
     * @return l'oggetto {@link java.nio.file.Path} prodotto o recuperato
     * @see java.nio.file.Path
     *  
     */
    private static Path runtimePath(Properties p) {
        return Path.of(p.getProperty("edge.pathRuntimeConfig", p.getProperty("edge.pathDatabase", "data/edge.db") + ".runtime.properties"));
    }

    /**
     * Salva configurazione per il dispositivo edge.
     *
     * @param heartbeat segnale di presenza da utilizzare
     * @param soglia soglia da utilizzare
     * @param qos livello MQTT di qualità del servizio
     * @param broker indirizzo del broker MQTT
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    public synchronized void salvaConfigurazione(int heartbeat, int soglia, int qos, String broker) throws IOException {
        Properties saved = new Properties();
        saved.setProperty("edge.intervalloHeartbeatSecondi", String.valueOf(heartbeat));
        saved.setProperty("edge.sogliaOfflineSecondi", String.valueOf(soglia));
        saved.setProperty("mqtt.qos", String.valueOf(qos));
        saved.setProperty("mqtt.brokerLocale", broker);
        Path destination = runtimePath(properties).toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "edge-config-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temporary)) { saved.store(out, "Configurazione ricevuta dal servizio locali"); }
            try { Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            properties.putAll(saved);
        } finally { Files.deleteIfExists(temporary); }
    }

    /**
     * Restituisce il livello di qualità del servizio MQTT.
     *
     * @return livello di qualità del servizio MQTT
     *  
     */
    public int mqttQos() { return Integer.parseInt(properties.getProperty("mqtt.qos", "2")); }

    /**
     * Restituisce l'identificativo del locale configurato.
     *
     * @return identificativo del locale
     *  
     */
    public long localeId() {
        return Long.parseLong(require("edge.localeId"));
    }

    /**
     * Restituisce l'identificativo del bersaglio configurato.
     *
     * @return identificativo del bersaglio
     *  
     */
    public long dartboardId() {
        return Long.parseLong(require("edge.dartboardId"));
    }

    /**
     * Restituisce l'identificativo del gioco configurato.
     *
     * @return identificativo del gioco
     *  
     */
    public long gameTypeId() {
        return Long.parseLong(require("edge.gameTypeId"));
    }

    /**
     * Restituisce il percorso del truststore del gateway.
     *
     * @return percorso del truststore del gateway
     *  
     */
    public String gatewayTrustStorePath() {
        return properties.getProperty("edge.gatewayTrustStorePath", "").trim();
    }

    /**
     * Restituisce la password del truststore del gateway.
     *
     * @return password del truststore del gateway
     *  
     */
    public String gatewayTrustStorePassword() {
        return properties.getProperty("edge.gatewayTrustStorePassword", "");
    }

    /**
     * Restituisce il token amministrativo del monitor locale.
     *
     * @return token amministrativo del monitor locale
     *  
     */
    public String displayAdminToken() {
        String environment = System.getenv("DARTHUB_EDGE_DISPLAY_ADMIN_TOKEN");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        String configured = properties.getProperty("edge.displayAdminToken", "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        Path tokenFile = Path.of(properties.getProperty("edge.pathDisplayAdminToken",
                "data/edge-display-" + dartboardId() + ".secret"));
        try {
            if (!Files.exists(tokenFile)) {
                Files.createDirectories(tokenFile.toAbsolutePath().getParent());
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                try {
                    Files.writeString(tokenFile, generated, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {

                }
            }
            return Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere o creare il token del display: " + tokenFile, e);
        }
    }

    /**
     * Restituisce l'indirizzo del broker MQTT locale.
     *
     * @return indirizzo del broker MQTT locale
     *  
     */
    public String brokerLocale() {
        return properties.getProperty("mqtt.brokerLocale", "tcp://localhost:1883");
    }

    /**
     * Restituisce l'indirizzo del broker MQTT centrale.
     *
     * @return indirizzo del broker MQTT centrale
     *  
     */
    public String brokerCentrale() {
        return properties.getProperty("mqtt.brokerCentrale", "tcp://localhost:1883");
    }

    /**
     * Restituisce il nome utente della connessione MQTT.
     *
     * @return nome utente della connessione MQTT
     *  
     */
    public String mqttUsername() {
        String value = properties.getProperty("mqtt.username", "");
        return value.isBlank() ? null : value;
    }

    /**
     * Restituisce la password della connessione MQTT.
     *
     * @return password della connessione MQTT
     *  
     */
    public char[] mqttPassword() {
        return properties.getProperty("mqtt.password", "").toCharArray();
    }

    /**
     * Restituisce l'indirizzo del gateway.
     *
     * @return indirizzo del gateway
     *  
     */
    public String gatewayUrl() {

        return properties.getProperty("edge.gatewayUrl", "https://localhost:8443");
    }

    /**
     * Restituisce il token del dispositivo edge.
     *
     * @return token del dispositivo edge
     * @throws IllegalArgumentException se viene rilevato l'errore «edge.tokenEdgeDevice mancante e nessun token persistito: lancia 'java -jar edge-component.jar --register --username &lt;locale-admin&gt; --serialNumber &lt;seriale&gt;' per registrarti automaticamente»
     *  
     */
    public String tokenEdgeDevice() {
        var persistito = it.uniupo.pissir.darts.edge.bootstrap.EdgeBootstrap.tokenPersistito(this);
        if (persistito.isPresent()) {
            return persistito.get();
        }
        String value = properties.getProperty("edge.tokenEdgeDevice");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("edge.tokenEdgeDevice mancante e nessun token persistito: lancia "
                    + "'java -jar edge-component.jar --register --username <locale-admin> "
                    + "--serialNumber <seriale>' per registrarti automaticamente");
        }
        return value;
    }

    /**
     * Restituisce il numero seriale del dispositivo.
     *
     * @return numero seriale del dispositivo
     *  
     */
    public String serialNumber() {
        return require("edge.serialNumber");
    }

    /**
     * Restituisce l'indirizzo del locale servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String localeServiceUrl() {
        return properties.getProperty("edge.localeServiceUrl", "http://localhost:7002");
    }

    /**
     * Restituisce l'indirizzo dell'utente servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String userServiceUrl() {
        return properties.getProperty("edge.userServiceUrl", "http://localhost:7001");
    }

    /**
     * Restituisce il percorso del database SQLite.
     *
     * @return percorso del database SQLite
     *  
     */
    public String pathDatabase() {
        return properties.getProperty("edge.pathDatabase", "data/edge.db");
    }

    /**
     * Restituisce il percorso del token del dispositivo.
     *
     * @return percorso del token del dispositivo
     *  
     */
    public String pathToken() {
        return properties.getProperty("edge.pathToken", "data/edge-token.secret");
    }

    /**
     * Restituisce la porta HTTP del monitor locale.
     *
     * @return porta HTTP del monitor locale
     *  
     */
    public int portaDisplay() {
        return Integer.parseInt(properties.getProperty("edge.portaDisplay", "7100"));
    }

    /**
     * Restituisce l'intervallo dei segnali di presenza.
     *
     * @return intervallo dei segnali di presenza
     *  
     */
    public int intervalloHeartbeatSecondi() {
        return Integer.parseInt(properties.getProperty("edge.intervalloHeartbeatSecondi", "30"));
    }

    /**
     * Restituisce l'intervallo iniziale fra i tentativi di sincronizzazione.
     *
     * @return intervallo iniziale fra i tentativi di sincronizzazione
     *  
     */
    public int syncRetryBaseSecondi() {
        return Integer.parseInt(properties.getProperty("edge.syncRetryBaseSecondi", "5"));
    }

    /**
     * Restituisce l'intervallo massimo fra i tentativi di sincronizzazione.
     *
     * @return intervallo massimo fra i tentativi di sincronizzazione
     *  
     */
    public int syncRetryMaxSecondi() {
        return Integer.parseInt(properties.getProperty("edge.syncRetryMaxSecondi", "300"));
    }

    /**
     * Restituisce il numero di connessioni del pool JDBC.
     *
     * @return numero di connessioni del pool JDBC
     *  
     */
    public int databasePoolSize() {
        return Integer.parseInt(properties.getProperty("database.poolSize", "4"));
    }

    /**
     * Verifica il requisito  prima di eseguire l'operazione.
     *
     * @param key chiave da cercare o utilizzare
     * @return il testo o il valore di configurazione richiesto
     * @throws IllegalArgumentException se i dati o lo stato del componente non consentono l'operazione
     *  
     */
    private String require(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Proprietà obbligatoria mancante: " + key);
        }
        return value;
    }

}
