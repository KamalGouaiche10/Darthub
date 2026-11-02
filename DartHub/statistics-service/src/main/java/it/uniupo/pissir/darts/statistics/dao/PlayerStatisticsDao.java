package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.statistics.model.PlayerStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO di player_statistics.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class PlayerStatisticsDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code PlayerStatisticsDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public PlayerStatisticsDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca il giocatore per identificativo.
     *
     * @param playerId identificativo del giocatore
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<PlayerStatistics> findById(long playerId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_statistics WHERE player_id = ?")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<PlayerStatistics>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Legge la classifica secondo i criteri indicati.
     *
     * @param page parametri di paginazione
     * @param ordinaPer ordina per da utilizzare
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<PlayerStatistics> classifica(Page page, String ordinaPer) {
        String colonna = "puntiTotali".equals(ordinaPer) ? "punti_totali" : "percentuale_vittorie";
        String sql = "SELECT ps.* FROM player_statistics ps "
                + "LEFT JOIN player_visibility pv ON pv.player_id = ps.player_id "
                + "WHERE pv.visibile IS NULL OR pv.visibile = 1 "
                + "ORDER BY ps." + colonna + " DESC, ps.punti_totali DESC LIMIT ? OFFSET ?";
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, page.limit());
                ps.setInt(2, page.offset());
                try (ResultSet rs = ps.executeQuery()) {
                    List<PlayerStatistics> risultato = new ArrayList<>();
                    while (rs.next()) {
                        risultato.add(map(rs));
                    }
                    return risultato;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Applica risultato partita per il giocatore.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param playerId identificativo del giocatore
     * @param punteggioFinale punteggio alla fine della partita
     * @param vincitore partecipante vincitore
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void applicaRisultatoPartita(Connection connection, long playerId, int punteggioFinale, boolean vincitore,
                                        Instant timestamp) {
        String upsert = "INSERT INTO player_statistics(player_id, partite_giocate, vittorie, percentuale_vittorie, "
                + "punti_totali, punteggio_medio, migliore_tiro, updated_at) VALUES (?,1,?,0,?,0,0,?) "
                + "ON CONFLICT(player_id) DO UPDATE SET "
                + "partite_giocate = player_statistics.partite_giocate + 1, "
                + "vittorie = player_statistics.vittorie + excluded.vittorie, "
                + "punti_totali = player_statistics.punti_totali + excluded.punti_totali, "
                + "updated_at = excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(upsert)) {
            ps.setLong(1, playerId);
            ps.setInt(2, vincitore ? 1 : 0);
            ps.setInt(3, punteggioFinale);
            ps.setString(4, timestamp.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
        ricalcolaMedie(connection, playerId);
    }

    /**
     * Aggiorna miglior tiro per il giocatore.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param playerId identificativo del giocatore
     * @param punti punti assegnati al tiro
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void aggiornaMigliorTiro(Connection connection, long playerId, int punti, Instant timestamp) {
        String sql = "INSERT INTO player_statistics(player_id, partite_giocate, vittorie, percentuale_vittorie, "
                + "punti_totali, punteggio_medio, migliore_tiro, updated_at) VALUES (?,0,0,0,0,0,?,?) "
                + "ON CONFLICT(player_id) DO UPDATE SET "
                + "migliore_tiro = MAX(player_statistics.migliore_tiro, excluded.migliore_tiro), "
                + "updated_at = excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setInt(2, punti);
            ps.setString(3, timestamp.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Ricalcola medie per il giocatore.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param playerId identificativo del giocatore
     * @see java.sql.Connection
     *  
     */
    private void ricalcolaMedie(Connection connection, long playerId) {
        String sql = "UPDATE player_statistics SET "
                + "percentuale_vittorie = CASE WHEN partite_giocate = 0 THEN 0 ELSE vittorie * 100.0 / partite_giocate END, "
                + "punteggio_medio = CASE WHEN partite_giocate = 0 THEN 0 ELSE punti_totali * 1.0 / partite_giocate END "
                + "WHERE player_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.model.PlayerStatistics} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.statistics.model.PlayerStatistics
     *  
     */
    private PlayerStatistics map(ResultSet rs) throws SQLException {
        return new PlayerStatistics(rs.getLong("player_id"), rs.getInt("partite_giocate"), rs.getInt("vittorie"),
                rs.getDouble("percentuale_vittorie"), rs.getInt("punti_totali"), rs.getDouble("punteggio_medio"),
                rs.getInt("migliore_tiro"), Instant.parse(rs.getString("updated_at")));
    }
}
