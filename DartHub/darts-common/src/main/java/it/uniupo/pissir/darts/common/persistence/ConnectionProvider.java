package it.uniupo.pissir.darts.common.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Gestisce il pool di connessioni SQLite e l'accesso alle transazioni. 
 */
public final class ConnectionProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionProvider.class);

    private final String jdbcUrl;
    private final ArrayBlockingQueue<Connection> pool;
    private final ReentrantLock writeLock = new ReentrantLock();

    /**
     * Inizializza {@code ConnectionProvider} con i dati e le dipendenze indicati.
     *
     * @param databasePath percorso del database SQLite
     * @param poolSize numero di connessioni del pool
     *  
     */
    public ConnectionProvider(String databasePath, int poolSize) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath;
        this.pool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.add(openConnection());
        }
        LOG.info("ConnectionProvider pronto per {} ({} connessioni)", jdbcUrl, poolSize);
    }

    /**
     * Apre una connessione SQLite e ne configura i parametri.
     *
     * @return l'oggetto {@link java.sql.Connection} prodotto o recuperato
     * @see java.sql.Connection
     *  
     */
    private Connection openConnection() {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Impossibile aprire la connessione SQLite: " + jdbcUrl, e);
        }
    }

    /**
     * Preleva una connessione dal pool ed esegue l'operazione richiesta.
     *
     * @param <T> tipo degli elementi gestiti
     * @param action azione richiesta al simulatore
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.util.function.Function
     *  
     */
    public <T> T withConnection(Function<Connection, T> action) {
        Connection connection = borrow();
        try {
            return action.apply(connection);
        } finally {
            release(connection);
        }
    }

    /**
     * Esegue l'operazione all'interno di una transazione.
     *
     * @param <T> tipo degli elementi gestiti
     * @param action azione richiesta al simulatore
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.util.function.Function
     *  
     */
    public <T> T withTransaction(Function<Connection, T> action) {
        writeLock.lock();
        Connection connection = borrow();
        try {
            return TransactionManager.runInTransaction(connection, action);
        } finally {
            release(connection);
            writeLock.unlock();
        }
    }

    /**
     * Preleva una connessione disponibile dal pool.
     *
     * @return l'oggetto {@link java.sql.Connection} prodotto o recuperato
     * @see java.sql.Connection
     *  
     */
    private Connection borrow() {
        try {
            return pool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrotto durante l'attesa di una connessione", e);
        }
    }

    /**
     * Restituisce la connessione al pool.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @see java.sql.Connection
     *  
     */
    private void release(Connection connection) {
        pool.offer(connection);
    }

    /**
     * Chiude il componente e rilascia le risorse utilizzate. 
     */
    @Override
    public void close() {
        for (Connection connection : pool) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warn("Chiusura della connessione fallita", e);
            }
        }
    }
}
