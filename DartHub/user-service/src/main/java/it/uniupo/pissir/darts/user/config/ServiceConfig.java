package it.uniupo.pissir.darts.user.config;

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
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.config.ServiceConfig} prodotto o recuperato
     *  
     */
    public static ServiceConfig load() {
        Properties properties = new Properties();
        String overridePath = System.getProperty("config.file");
        if (overridePath != null) {
            try (InputStream in = Files.newInputStream(Path.of(overridePath))) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Lettura di " + overridePath + " fallita", e);
            }
        } else {
            try (InputStream in = ServiceConfig.class.getClassLoader().getResourceAsStream("user-service.properties")) {
                if (in == null) {
                    throw new IllegalStateException("user-service.properties non trovato nel classpath");
                }
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Lettura di user-service.properties fallita", e);
            }
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
        return Integer.parseInt(properties.getProperty("server.port", "7001"));
    }

    /**
     * Restituisce il percorso del database SQLite.
     *
     * @return percorso del database SQLite
     *  
     */
    public String databasePath() {
        return properties.getProperty("database.path", "data/user.db");
    }

    /**
     * Restituisce il percorso della chiave di firma JWT.
     *
     * @return percorso della chiave di firma JWT
     *  
     */
    public String jwtSigningKeyPath() {
        return properties.getProperty("jwt.signingKeyPath", "data/jwt-signing-key.json");
    }

    /**
     * Restituisce la credenziale delle chiamate interne.
     *
     * @return credenziale delle chiamate interne
     *  
     */
    public String internalServiceSecret() {
        return require(properties, "internal.serviceSecret");
    }

    /**
     * Verifica il requisito  prima di eseguire l'operazione.
     *
     * @param properties proprietà di configurazione
     * @param key chiave da cercare o utilizzare
     * @return il testo o il valore di configurazione richiesto
     * @throws IllegalArgumentException se i dati o lo stato del componente non consentono l'operazione
     * @see java.util.Properties
     *  
     */
    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Proprietà obbligatoria mancante: " + key);
        }
        return value;
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
        return properties.getProperty("mqtt.clientId", "user-service");
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
