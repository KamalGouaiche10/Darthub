package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.tournament.model.BracketSlot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella bracket_slots, solo per la formula ELIMINAZIONE (vedi schema.sql). 
 */
public final class BracketSlotDao {

    /**
     * Cerca bracket posizione nel tabellone per turno.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param turno turno da elaborare
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<BracketSlot> findByTurno(Connection connection, long tournamentId, int turno) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM bracket_slots WHERE tournament_id = ? AND turno = ? ORDER BY slot")) {
            ps.setLong(1, tournamentId);
            ps.setInt(2, turno);
            try (ResultSet rs = ps.executeQuery()) {
                List<BracketSlot> risultato = new ArrayList<>();
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
     * Cerca il bracket posizione nel tabellone usando i dati della richiesta.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<BracketSlot> find(Connection connection, long tournamentId, int turno, int slot) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM bracket_slots WHERE tournament_id = ? AND turno = ? AND slot = ?")) {
            ps.setLong(1, tournamentId);
            ps.setInt(2, turno);
            ps.setInt(3, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce o aggiorna il bracket posizione nel tabellone nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @param participantId identificativo del partecipante
     * @param matchId identificativo della partita
     * @see java.sql.Connection
     *  
     */
    public void upsert(Connection connection, long tournamentId, int turno, int slot, Long participantId, Long matchId) {
        String sql = "INSERT INTO bracket_slots(tournament_id, turno, slot, participant_id, match_id) VALUES (?,?,?,?,?) "
                + "ON CONFLICT(tournament_id, turno, slot) DO UPDATE SET "
                + "participant_id = excluded.participant_id, match_id = excluded.match_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, tournamentId);
            ps.setInt(2, turno);
            ps.setInt(3, slot);
            ps.setObject(4, participantId);
            ps.setObject(5, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Elimina gli elementi associati al contesto indicato.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @see java.sql.Connection
     *  
     */
    public void deleteAll(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM bracket_slots WHERE tournament_id = ?")) {
            ps.setLong(1, tournamentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.BracketSlot} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.tournament.model.BracketSlot
     *  
     */
    private BracketSlot map(ResultSet rs) throws SQLException {
        return new BracketSlot(rs.getLong("tournament_id"), rs.getInt("turno"), rs.getInt("slot"),
                rs.getObject("participant_id") != null ? rs.getLong("participant_id") : null,
                rs.getObject("match_id") != null ? rs.getLong("match_id") : null);
    }
}
