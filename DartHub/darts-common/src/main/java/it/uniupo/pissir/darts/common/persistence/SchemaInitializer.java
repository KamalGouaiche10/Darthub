package it.uniupo.pissir.darts.common.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Legge ed esegue lo script SQL iniziale del database. 
 */
public final class SchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private SchemaInitializer() {
    }

    /**
     * Esegue lo script SQL per inizializzare il database.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param schemaResourcePath percorso dello script SQL nel classpath
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public static void initialize(ConnectionProvider connectionProvider, String schemaResourcePath) {
        String sql = SqlScripts.readResource(schemaResourcePath);
        connectionProvider.withTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String stmt : SqlScripts.splitStatements(sql)) {
                    statement.execute(stmt);
                }
                return null;
            } catch (SQLException e) {
                throw new IllegalStateException("Inizializzazione schema fallita: " + schemaResourcePath, e);
            }
        });
        LOG.info("Schema inizializzato da {}", schemaResourcePath);
    }
}
