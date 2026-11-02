package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.tournament.model.Standing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
/**
 * DAO della tabella standings: classifica di un torneo.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class StandingDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code StandingDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public StandingDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca righe di classifica per torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Standing> findByTournament(long tournamentId) {
        return connectionProvider.withConnection(connection -> findByTournament(connection, tournamentId));
    }

    /**
     * Cerca righe di classifica per torneo.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Standing> findByTournament(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM standings WHERE tournament_id = ? ORDER BY punti DESC, vittorie DESC, partite_giocate ASC")) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Standing> risultato = new ArrayList<>();
                while (rs.next()) {
                    risultato.add(map(rs));
                }
                return risultato;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Applica risultato per la classifica.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @param puntiDelta punti delta da utilizzare
     * @param vittoria indicatore di vittoria
     * @see java.sql.Connection
     *  
     */
    public void applicaRisultato(Connection connection, long tournamentId, long participantId, int puntiDelta,
                                  boolean vittoria) {
        String sql = "INSERT INTO standings(tournament_id, participant_id, punti, partite_giocate, vittorie) "
                + "VALUES (?,?,?,1,?) "
                + "ON CONFLICT(tournament_id, participant_id) DO UPDATE SET "
                + "punti = punti + excluded.punti, partite_giocate = partite_giocate + 1, "
                + "vittorie = vittorie + excluded.vittorie";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, tournamentId);
            ps.setLong(2, participantId);
            ps.setInt(3, puntiDelta);
            ps.setInt(4, vittoria ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna posizione per la classifica.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @param posizione posizione in classifica
     * @see java.sql.Connection
     *  
     */
    public void aggiornaPosizione(Connection connection, long tournamentId, long participantId, int posizione) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE standings SET posizione = ? WHERE tournament_id = ? AND participant_id = ?")) {
            ps.setInt(1, posizione);
            ps.setLong(2, tournamentId);
            ps.setLong(3, participantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Standing} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.tournament.model.Standing
     *  
     */
    private Standing map(ResultSet rs) throws SQLException {
        return new Standing(rs.getLong("tournament_id"), rs.getLong("participant_id"), rs.getInt("punti"),
                rs.getInt("partite_giocate"), rs.getInt("vittorie"),
                rs.getObject("posizione") != null ? rs.getInt("posizione") : null);
    }
}
