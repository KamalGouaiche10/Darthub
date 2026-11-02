package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.statistics.model.TeamStatistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * DAO di team_statistics.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class TeamStatisticsDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code TeamStatisticsDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TeamStatisticsDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca la squadra per identificativo.
     *
     * @param teamId identificativo della squadra
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<TeamStatistics> findById(long teamId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM team_statistics WHERE team_id = ?")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<TeamStatistics>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Applica risultato partita per la squadra.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param teamId identificativo della squadra
     * @param punteggioFinale punteggio alla fine della partita
     * @param vincitore partecipante vincitore
     * @param timestamp istante dell'evento
     * @see java.sql.Connection
     *  
     */
    public void applicaRisultatoPartita(Connection connection, long teamId, int punteggioFinale, boolean vincitore,
                                        Instant timestamp) {
        String upsert = "INSERT INTO team_statistics(team_id, partite_giocate, vittorie, percentuale_vittorie, "
                + "punti_totali, punteggio_medio, updated_at) VALUES (?,1,?,0,?,0,?) "
                + "ON CONFLICT(team_id) DO UPDATE SET "
                + "partite_giocate = team_statistics.partite_giocate + 1, "
                + "vittorie = team_statistics.vittorie + excluded.vittorie, "
                + "punti_totali = team_statistics.punti_totali + excluded.punti_totali, "
                + "updated_at = excluded.updated_at";
        try (PreparedStatement ps = connection.prepareStatement(upsert)) {
            ps.setLong(1, teamId);
            ps.setInt(2, vincitore ? 1 : 0);
            ps.setInt(3, punteggioFinale);
            ps.setString(4, timestamp.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
        String ricalcolo = "UPDATE team_statistics SET "
                + "percentuale_vittorie = CASE WHEN partite_giocate = 0 THEN 0 ELSE vittorie * 100.0 / partite_giocate END, "
                + "punteggio_medio = CASE WHEN partite_giocate = 0 THEN 0 ELSE punti_totali * 1.0 / partite_giocate END "
                + "WHERE team_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(ricalcolo)) {
            ps.setLong(1, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.model.TeamStatistics} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.statistics.model.TeamStatistics
     *  
     */
    private TeamStatistics map(ResultSet rs) throws SQLException {
        return new TeamStatistics(rs.getLong("team_id"), rs.getInt("partite_giocate"), rs.getInt("vittorie"),
                rs.getDouble("percentuale_vittorie"), rs.getInt("punti_totali"), rs.getDouble("punteggio_medio"),
                Instant.parse(rs.getString("updated_at")));
    }
}
