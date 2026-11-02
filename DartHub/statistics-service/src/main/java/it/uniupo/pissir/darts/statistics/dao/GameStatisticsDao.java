package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.statistics.model.GameStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO di game_statistics.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class GameStatisticsDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code GameStatisticsDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public GameStatisticsDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca il gioco statistics per identificativo.
     *
     * @param gameTypeId identificativo del gioco
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<GameStatistics> findById(long gameTypeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM game_statistics WHERE game_type_id = ?")) {
                ps.setLong(1, gameTypeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<GameStatistics>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Registra partita per il gioco statistics.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param gameTypeId identificativo del gioco
     * @param numeroPartecipanti numero partecipanti da utilizzare
     * @see java.sql.Connection
     *  
     */
    public void registraPartita(Connection connection, long gameTypeId, int numeroPartecipanti) {
        GameStatistics attuale = leggi(connection, gameTypeId).orElse(new GameStatistics(gameTypeId, 0, 0));
        int nuovoTotale = attuale.partiteTotali() + 1;
        double nuovaMedia = (attuale.mediaGiocatoriPerPartita() * attuale.partiteTotali() + numeroPartecipanti) / nuovoTotale;
        String sql = "INSERT INTO game_statistics(game_type_id, partite_totali, media_giocatori_per_partita) "
                + "VALUES (?,?,?) ON CONFLICT(game_type_id) DO UPDATE SET "
                + "partite_totali = excluded.partite_totali, media_giocatori_per_partita = excluded.media_giocatori_per_partita";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, gameTypeId);
            ps.setInt(2, nuovoTotale);
            ps.setDouble(3, nuovaMedia);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Carica il gioco statistics dalla sorgente configurata.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param gameTypeId identificativo del gioco
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    private Optional<GameStatistics> leggi(Connection connection, long gameTypeId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM game_statistics WHERE game_type_id = ?")) {
            ps.setLong(1, gameTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.model.GameStatistics} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.statistics.model.GameStatistics
     *  
     */
    private GameStatistics map(ResultSet rs) throws SQLException {
        return new GameStatistics(rs.getLong("game_type_id"), rs.getInt("partite_totali"),
                rs.getDouble("media_giocatori_per_partita"));
    }
}
