package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO della tabella di giunzione team_members.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class TeamMemberDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code TeamMemberDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TeamMemberDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca componenti della squadra giocatore identificativi per squadra.
     *
     * @param teamId identificativo della squadra
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findPlayerIdsByTeam(long teamId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT player_id FROM team_members WHERE team_id = ? ORDER BY player_id")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> risultato = new ArrayList<>();
                    while (rs.next()) {
                        risultato.add(rs.getLong(1));
                    }
                    return risultato;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Indica se member per i dati forniti.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     * @return {@code true} se member per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isMember(long teamId, long playerId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM team_members WHERE team_id = ? AND player_id = ?")) {
                ps.setLong(1, teamId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il componente della squadra nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     * @see java.sql.Connection
     *  
     */
    public void insert(Connection connection, long teamId, long playerId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO team_members(team_id, player_id) VALUES (?,?)")) {
            ps.setLong(1, teamId);
            ps.setLong(2, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message != null && message.contains("team_members")) {
                throw new ConflictException("MEMBRO_GIA_PRESENTE", "Il giocatore è già membro di questa squadra");
            }
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce il componente della squadra nel database.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     *  
     */
    public void insert(long teamId, long playerId) {
        connectionProvider.withTransaction(connection -> {
            insert(connection, teamId, playerId);
            return null;
        });
    }

    /**
     * Elimina il componente della squadra identificato dalla richiesta.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     *  
     */
    public void delete(long teamId, long playerId) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM team_members WHERE team_id = ? AND player_id = ?")) {
                ps.setLong(1, teamId);
                ps.setLong(2, playerId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }
}
