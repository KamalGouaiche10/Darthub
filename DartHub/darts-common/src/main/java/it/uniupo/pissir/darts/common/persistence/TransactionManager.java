package it.uniupo.pissir.darts.common.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Esegue un'operazione JDBC con commit o rollback della transazione. 
 */
public final class TransactionManager {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private TransactionManager() {
    }

    /**
     * Esegue l'operazione con commit oppure rollback in caso di errore.
     *
     * @param <T> tipo degli elementi gestiti
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param action azione richiesta al simulatore
     * @return l'oggetto {@code T} prodotto o recuperato
     * @see java.sql.Connection
     *  
     */
    public static <T> T runInTransaction(Connection connection, Function<Connection, T> action) {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            T result = action.apply(connection);
            connection.commit();
            return result;
        } catch (RuntimeException e) {
            rollbackQuietly(connection);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IllegalStateException("Errore durante la transazione", e);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
    }

    /**
     * Tenta il rollback conservando la causa dell'errore originale.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @see java.sql.Connection
     *  
     */
    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {

        }
    }

    /**
     * Ripristina l'impostazione di autocommit della connessione.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param previousAutoCommit impostazione di autocommit da ripristinare
     * @see java.sql.Connection
     *  
     */
    private static void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {

        }
    }
}
