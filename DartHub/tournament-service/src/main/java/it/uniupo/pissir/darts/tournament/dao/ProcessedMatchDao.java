package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Legge e modifica partite elaborate nel database del servizio. 
 */
public final class ProcessedMatchDao {
    /**
     * Verifica se la risorsa indicata è presente.
     *
     * @param c carattere da elaborare
     * @param matchId identificativo della partita
     * @return {@code true} se la risorsa indicata è presente; {@code false} altrimenti
     * @see java.sql.Connection
     *  
     */
    public boolean exists(Connection c, long matchId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM processed_matches WHERE match_id=?")) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
    }
    /**
     * Registra lo stato .
     *
     * @param c carattere da elaborare
     * @param matchId identificativo della partita
     * @see java.sql.Connection
     *  
     */
    public void mark(Connection c, long matchId) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO processed_matches(match_id,processed_at) VALUES(?,?)")) {
            ps.setLong(1, matchId); ps.setString(2, Instant.now().toString()); ps.executeUpdate();
        } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
    }
}
