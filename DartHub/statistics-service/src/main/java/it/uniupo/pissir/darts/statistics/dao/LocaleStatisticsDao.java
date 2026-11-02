package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.statistics.model.LocaleStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * DAO di locale_statistics e della tabella di appoggio locale_players.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class LocaleStatisticsDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code LocaleStatisticsDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public LocaleStatisticsDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca locali per bersaglio.
     *
     * @param dartboardId identificativo del bersaglio
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<LocaleStatistics> findByDartboard(long dartboardId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM locale_statistics WHERE dartboard_id = ?")) {
                ps.setLong(1, dartboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<LocaleStatistics>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca locali per locale.
     *
     * @param localeId identificativo del locale
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<LocaleStatistics> findByLocale(long localeId) {
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT ? AS locale_id, 0 AS dartboard_id, COALESCE(SUM(numero_partite), 0) AS numero_partite, "
                    + "(SELECT COUNT(DISTINCT player_id) FROM locale_players WHERE locale_id = ?) AS giocatori_distinti, "
                    + "MAX(ultimo_utilizzo) AS ultimo_utilizzo FROM locale_statistics WHERE locale_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, localeId);
                ps.setLong(2, localeId);
                ps.setLong(3, localeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getString("ultimo_utilizzo") == null && rs.getInt("numero_partite") == 0) {
                        return Optional.<LocaleStatistics>empty();
                    }
                    return Optional.of(map(rs));
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Registra partita per il locale.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void registraPartita(Connection connection, long localeId, long dartboardId, Instant timestamp) {
        String sql = "INSERT INTO locale_statistics(locale_id, dartboard_id, numero_partite, giocatori_distinti, "
                + "ultimo_utilizzo) VALUES (?,?,1,0,?) "
                + "ON CONFLICT(locale_id, dartboard_id) DO UPDATE SET "
                + "numero_partite = locale_statistics.numero_partite + 1, "
                + "ultimo_utilizzo = excluded.ultimo_utilizzo";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, localeId);
            ps.setLong(2, dartboardId);
            ps.setString(3, timestamp.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Registra giocatore per il locale.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param playerId identificativo del giocatore
     * @see java.sql.Connection
     *  
     */
    public void registraGiocatore(Connection connection, long localeId, long dartboardId, long playerId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO locale_players(locale_id, dartboard_id, player_id) VALUES (?,?,?)")) {
            ps.setLong(1, localeId);
            ps.setLong(2, dartboardId);
            ps.setLong(3, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
        String sql = "UPDATE locale_statistics SET giocatori_distinti = "
                + "(SELECT COUNT(*) FROM locale_players WHERE locale_id = ? AND dartboard_id = ?) "
                + "WHERE locale_id = ? AND dartboard_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, localeId);
            ps.setLong(2, dartboardId);
            ps.setLong(3, localeId);
            ps.setLong(4, dartboardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.model.LocaleStatistics} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.statistics.model.LocaleStatistics
     *  
     */
    private LocaleStatistics map(ResultSet rs) throws SQLException {
        String ultimoUtilizzo = rs.getString("ultimo_utilizzo");
        return new LocaleStatistics(rs.getLong("locale_id"), rs.getLong("dartboard_id"), rs.getInt("numero_partite"),
                rs.getInt("giocatori_distinti"), ultimoUtilizzo == null ? null : Instant.parse(ultimoUtilizzo));
    }
}
