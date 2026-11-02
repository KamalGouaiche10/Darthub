package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Visibilità di un giocatore nelle statistiche, alimentata da eventi/utenti/conteggio-cambiato: un giocatore bloccato o un gestore non ancora approvato non compare in GET /statistiche/classifica né in GET /statistiche/giocatori/{id}.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class PlayerVisibilityDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code PlayerVisibilityDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public PlayerVisibilityDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Imposta visibile per il componente.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param playerId identificativo del giocatore
     * @param visibile indicatore di visibilità della risorsa
     * @see java.sql.Connection
     *  
     */
    public void setVisibile(Connection connection, long playerId, boolean visibile) {
        String sql = "INSERT INTO player_visibility(player_id, visibile) VALUES (?, ?) "
                + "ON CONFLICT(player_id) DO UPDATE SET visibile = excluded.visibile";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setInt(2, visibile ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Indica se visibile per i dati forniti.
     *
     * @param playerId identificativo del giocatore
     * @return {@code true} se visibile per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isVisibile(long playerId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT visibile FROM player_visibility WHERE player_id = ?")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return !rs.next() || rs.getInt("visibile") == 1;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }
}
