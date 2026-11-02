package it.uniupo.pissir.darts.gateway.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Carica e rende disponibili i parametri di configurazione del componente.
 *
 * @see java.util.Properties
 *  
 */
public final class GatewayConfig {

    private final Properties properties;

    /**
     * Inizializza {@code GatewayConfig} con i dati e le dipendenze indicati.
     *
     * @param properties proprietà di configurazione
     * @see java.util.Properties
     *  
     */
    private GatewayConfig(Properties properties) {
        this.properties = properties;
    }

    /**
     * Carica il gateway config dalla sorgente configurata.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.gateway.config.GatewayConfig} prodotto o recuperato
     *  
     */
    public static GatewayConfig load() {
        Properties properties = new Properties();
        String overridePath = System.getProperty("config.file");
        try (InputStream in = overridePath != null
                ? Files.newInputStream(Path.of(overridePath))
                : GatewayConfig.class.getClassLoader().getResourceAsStream("gateway.properties")) {
            if (in == null) {
                throw new IllegalStateException("gateway.properties non trovato nel classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Lettura della configurazione fallita", e);
        }
        return new GatewayConfig(properties);
    }

    /**
     * Restituisce la porta HTTPS del gateway.
     *
     * @return porta HTTPS del gateway
     *  
     */
    public int securePort() {
        return Integer.parseInt(properties.getProperty("server.securePort", "8443"));
    }

    /**
     * Restituisce la porta HTTP locale del gateway.
     *
     * @return porta HTTP locale del gateway
     *  
     */
    public int insecurePort() {
        return Integer.parseInt(properties.getProperty("server.insecurePort", "8080"));
    }

    /**
     * Restituisce l'indicatore di abilitazione del gateway HTTP locale.
     *
     * @return indicatore di abilitazione del gateway HTTP locale
     *  
     */
    public boolean localHttpEnabled() {
        return Boolean.getBoolean("gateway.localHttp");
    }

    /**
     * Restituisce il percorso del keystore HTTPS.
     *
     * @return percorso del keystore HTTPS
     *  
     */
    public String keystorePath() {
        return properties.getProperty("ssl.keystorePath", "keystore.p12");
    }

    /**
     * Restituisce la password del keystore HTTPS.
     *
     * @return password del keystore HTTPS
     *  
     */
    public String keystorePassword() {
        return properties.getProperty("ssl.keystorePassword", "changeit");
    }

    /**
     * Restituisce l'indirizzo dell'utente servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String userServiceBaseUrl() {
        return properties.getProperty("services.user.baseUrl", "http://localhost:7001");
    }

    /**
     * Restituisce l'indirizzo del locale servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String localeServiceBaseUrl() {
        return properties.getProperty("services.locale.baseUrl", "http://localhost:7002");
    }

    /**
     * Restituisce l'indirizzo del gioco servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String gameServiceBaseUrl() {
        return properties.getProperty("services.game.baseUrl", "http://localhost:7003");
    }

    /**
     * Restituisce l'indirizzo della partita servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String matchServiceBaseUrl() {
        return properties.getProperty("services.match.baseUrl", "http://localhost:7004");
    }

    /**
     * Restituisce l'indirizzo del torneo servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String tournamentServiceBaseUrl() {
        return properties.getProperty("services.tournament.baseUrl", "http://localhost:7005");
    }

    /**
     * Restituisce l'indirizzo del statistics servizio.
     *
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String statisticsServiceBaseUrl() {
        return properties.getProperty("services.statistics.baseUrl", "http://localhost:7006");
    }

    /**
     * Restituisce il tempo massimo di attesa del proxy.
     *
     * @return tempo massimo di attesa del proxy
     * @see java.time.Duration
     *  
     */
    public Duration proxyTimeout() {
        return Duration.ofSeconds(Long.parseLong(properties.getProperty("proxy.timeoutSeconds", "10")));
    }

    /**
     * Restituisce il tempo massimo di attesa dei controlli di salute.
     *
     * @return tempo massimo di attesa dei controlli di salute
     * @see java.time.Duration
     *  
     */
    public Duration healthCheckTimeout() {
        return Duration.ofSeconds(Long.parseLong(properties.getProperty("monitoring.healthTimeoutSeconds", "2")));
    }

    /**
     * Restituisce le origini HTTP ammesse dalla configurazione CORS.
     *
     * @return origini HTTP ammesse dalla configurazione CORS
     * @see java.util.List
     *  
     */
    public List<String> corsAllowedOrigins() {
        String value = properties.getProperty("cors.allowedOrigins", "");
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    /**
     * Restituisce l'indirizzo del broker MQTT.
     *
     * @return indirizzo del broker MQTT
     *  
     */
    public String mqttBrokerUrl() {
        return properties.getProperty("mqtt.brokerUrl", "tcp://localhost:1883");
    }

    /**
     * Restituisce l'identificativo del client MQTT.
     *
     * @return identificativo del client MQTT
     *  
     */
    public String mqttClientId() {
        String id = properties.getProperty("mqtt.clientId", "api-gateway");
        return localHttpEnabled() ? id + "-local" : id;
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
     * Restituisce le proprietà complete del componente.
     *
     * @return proprietà complete del componente
     * @see java.util.Properties
     *  
     */
    public Properties raw() {
        return properties;
    }
}
