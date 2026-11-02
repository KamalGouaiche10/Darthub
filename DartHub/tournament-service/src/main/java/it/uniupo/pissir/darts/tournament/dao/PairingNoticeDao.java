package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.SQLException;
import java.time.Instant;

/**
 * Legge e modifica abbinamenti nel database del servizio.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class PairingNoticeDao {
    private final ConnectionProvider connections;

    /**
     * Inizializza {@code PairingNoticeDao} con i dati e le dipendenze indicati.
     *
     * @param connections connections da utilizzare
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public PairingNoticeDao(ConnectionProvider connections) {
        this.connections = connections;
    }

    /**
     * Indica se lettura per i dati forniti.
     *
     * @param userId identificativo dell'utente
     * @param matchId identificativo della partita
     * @return {@code true} se lettura per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isRead(long userId, long matchId) {
        return connections.withConnection(connection -> {
            try (var ps = connection.prepareStatement(
                    "SELECT 1 FROM pairing_notice_reads WHERE user_id = ? AND match_id = ?")) {
                ps.setLong(1, userId);
                ps.setLong(2, matchId);
                try (var rs = ps.executeQuery()) { return rs.next(); }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Registra lo stato lettura.
     *
     * @param userId identificativo dell'utente
     * @param matchId identificativo della partita
     *  
     */
    public void markRead(long userId, long matchId) {
        connections.withTransaction(connection -> {
            try (var ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO pairing_notice_reads(user_id, match_id, read_at) VALUES (?,?,?)")) {
                ps.setLong(1, userId);
                ps.setLong(2, matchId);
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
                return null;
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }
}
