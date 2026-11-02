package it.uniupo.pissir.darts.match.support;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;

import java.nio.file.Path;

/**
 * Prepara un database isolato con schema e migrazioni per i test. 
 */
public final class TestDatabaseSupport {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private TestDatabaseSupport() {
    }

    /**
     * Prepara un database temporaneo con schema e migrazioni.
     *
     * @param tempDir cartella temporanea del test
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.persistence.ConnectionProvider} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public static ConnectionProvider freshDatabase(Path tempDir) {
        Path dbFile = tempDir.resolve("match-test-" + System.nanoTime() + ".db");
        ConnectionProvider connectionProvider = new ConnectionProvider(dbFile.toString(), 4);
        SchemaInitializer.initialize(connectionProvider, "schema.sql");
        MigrationRunner.run(connectionProvider, "migrations");
        return connectionProvider;
    }
}
