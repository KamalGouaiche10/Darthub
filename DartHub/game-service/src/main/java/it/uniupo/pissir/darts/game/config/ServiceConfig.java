package it.uniupo.pissir.darts.game.config;

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
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.config.ServiceConfig} prodotto o recuperato
     *  
     */
    public static ServiceConfig load() {
        Properties properties = new Properties();
        String overridePath = System.getProperty("config.file");
        try (InputStream in = overridePath != null
                ? Files.newInputStream(Path.of(overridePath))
                : ServiceConfig.class.getClassLoader().getResourceAsStream("game-service.properties")) {
            if (in == null) {
                throw new IllegalStateException("game-service.properties non trovato nel classpath");
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
        return Integer.parseInt(properties.getProperty("server.port", "7003"));
    }

    /**
     * Restituisce il percorso del database SQLite.
     *
     * @return percorso del database SQLite
     *  
     */
    public String databasePath() {
        return properties.getProperty("database.path", "data/game.db");
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
