package it.uniupo.pissir.darts.common.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fornisce le operazioni JDBC comuni agli oggetti di accesso ai dati.
 *
 * @param <T> tipo degli elementi gestiti
 * @param <ID> tipo dell'identificativo dell'entità
 * @see it.uniupo.pissir.darts.common.persistence.Dao
 *  
 */
public abstract class JdbcDao<T, ID> implements Dao<T, ID> {

    protected final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code JdbcDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    protected JdbcDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Fornisce il convertitore delle righe JDBC nel tipo gestito dal DAO.
     *
     * @return il convertitore delle righe JDBC nel tipo dell'entità
     * @see it.uniupo.pissir.darts.common.persistence.RowMapper
     *  
     */
    protected abstract RowMapper<T> rowMapper();

    /**
     * Esegue una query parametrizzata e legge al massimo una riga.
     *
     * @param sql istruzione SQL da eseguire
     * @param params valori da associare ai parametri SQL
     * @return la prima riga convertita, oppure un risultato vuoto se assente
     * @see java.util.Optional
     *  
     */
    protected Optional<T> queryOne(String sql, Object... params) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rowMapper().map(rs)) : Optional.<T>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Esegue una query parametrizzata e converte le righe trovate.
     *
     * @param sql istruzione SQL da eseguire
     * @param params valori da associare ai parametri SQL
     * @return l'elenco delle righe convertite, vuoto se la query non trova risultati
     * @see java.util.List
     *  
     */
    protected List<T> queryList(String sql, Object... params) {
        return connectionProvider.withConnection(connection -> {
            List<T> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(rowMapper().map(rs));
                    }
                }
                return results;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Esegue l'istruzione SQL che modifica le righe.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param sql istruzione SQL da eseguire
     * @param params valori da associare ai parametri SQL
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    protected int executeUpdate(Connection connection, String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Esegue l'inserimento e legge la chiave generata dal database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param sql istruzione SQL da eseguire
     * @param params valori da associare ai parametri SQL
     * @return la chiave generata dal database per la nuova riga
     * @see java.sql.Connection
     *  
     */
    protected long executeInsertReturningId(Connection connection, String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new IllegalStateException("Nessuna chiave generata per: " + sql);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Associa i valori ai parametri dell'istruzione JDBC.
     *
     * @param ps istruzione JDBC parametrizzata
     * @param params valori da associare ai parametri SQL
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.sql.PreparedStatement
     *  
     */
    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
