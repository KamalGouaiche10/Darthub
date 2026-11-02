package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.statistics.model.GlobalStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * DAO di global_statistics (riga singola, 6).
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class GlobalStatisticsDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code GlobalStatisticsDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public GlobalStatisticsDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca il global statistics usando i dati della richiesta.
     *
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<GlobalStatistics> find() {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM global_statistics WHERE id = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<GlobalStatistics>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Incrementa il conteggio delle partite registrate.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void incrementaPartiteTotali(Connection connection, Instant timestamp) {
        String sql = "INSERT INTO global_statistics(id, utenti_totali, locali_totali, partite_totali, tornei_totali, "
                + "updated_at) VALUES (1,0,0,1,0,?) "
                + "ON CONFLICT(id) DO UPDATE SET partite_totali = global_statistics.partite_totali + 1, "
                + "updated_at = excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, timestamp.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Applica delta utenti per il global statistics.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param delta variazione da applicare al valore
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void applicaDeltaUtenti(Connection connection, int delta, Instant timestamp) {
        String sql = "INSERT INTO global_statistics(id, utenti_totali, locali_totali, partite_totali, tornei_totali, "
                + "updated_at) VALUES (1, MAX(0, ?), 0, 0, 0, ?) "
                + "ON CONFLICT(id) DO UPDATE SET utenti_totali = MAX(0, global_statistics.utenti_totali + ?), "
                + "updated_at = excluded.updated_at";
        applicaDelta(connection, sql, delta, timestamp);
    }

    /**
     * Applica delta locali per il global statistics.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param delta variazione da applicare al valore
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void applicaDeltaLocali(Connection connection, int delta, Instant timestamp) {
        String sql = "INSERT INTO global_statistics(id, utenti_totali, locali_totali, partite_totali, tornei_totali, "
                + "updated_at) VALUES (1, 0, MAX(0, ?), 0, 0, ?) "
                + "ON CONFLICT(id) DO UPDATE SET locali_totali = MAX(0, global_statistics.locali_totali + ?), "
                + "updated_at = excluded.updated_at";
        applicaDelta(connection, sql, delta, timestamp);
    }

    /**
     * Applica delta per il global statistics.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param sql istruzione SQL da eseguire
     * @param delta variazione da applicare al valore
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    private void applicaDelta(Connection connection, String sql, int delta, Instant timestamp) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, timestamp.toString());
            ps.setInt(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.model.GlobalStatistics} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.statistics.model.GlobalStatistics
     *  
     */
    private GlobalStatistics map(ResultSet rs) throws SQLException {
        return new GlobalStatistics(rs.getInt("utenti_totali"), rs.getInt("locali_totali"), rs.getInt("partite_totali"),
                rs.getInt("tornei_totali"), Instant.parse(rs.getString("updated_at")));
    }
}
