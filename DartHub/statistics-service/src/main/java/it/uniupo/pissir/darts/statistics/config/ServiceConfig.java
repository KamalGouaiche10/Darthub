package it.uniupo.pissir.darts.statistics.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Carica e rende disponibili i parametri di configurazione del componente.
 *
 * @see java.util.Properties
 *  
 */
public final class ServiceConfig {

    private final Properties properties;

    /**
     * Inizializza {@code ServiceConfig} con i dati e le dipendenze indicati.
     *
     * @param properties proprietà di configurazione
     * @see java.util.Properties
     *  
     */
    private ServiceConfig(Properties properties) {
        this.properties = properties;
    }

    /**
     * Carica il servizio config dalla sorgente configurata.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.config.ServiceConfig} prodotto o recuperato
     *  
     */
    public static ServiceConfig load() {
        Properties properties = new Properties();
        String overridePath = System.getProperty("config.file");
        try (InputStream in = overridePath != null
                ? Files.newInputStream(Path.of(overridePath))
                : ServiceConfig.class.getClassLoader().getResourceAsStream("statistics-service.properties")) {
            if (in == null) {
                throw new IllegalStateException("statistics-service.properties non trovato nel classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Lettura della configurazione fallita", e);
        }
        return new ServiceConfig(properties);
    }

    /**
     * Restituisce la porta HTTP del servizio.
     *
     * @return porta HTTP del servizio
     *  
     */
    public int port() {
        return Integer.parseInt(properties.getProperty("server.port", "7006"));
    }

    /**
     * Restituisce il percorso del database SQLite.
     *
     * @return percorso del database SQLite
     *  
     */
    public String databasePath() {
        return properties.getProperty("database.path", "data/statistics.db");
    }

    /**
     * Restituisce il numero di connessioni del pool JDBC.
     *
     * @return numero di connessioni del pool JDBC
     *  
     */
    public int databasePoolSize() {
        return Integer.parseInt(properties.getProperty("database.poolSize", "8"));
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
        return properties.getProperty("mqtt.clientId", "statistics-service");
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
        String value = properties.getProperty("mqtt.password", "");
        return value.isBlank() ? null : value.toCharArray();
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
