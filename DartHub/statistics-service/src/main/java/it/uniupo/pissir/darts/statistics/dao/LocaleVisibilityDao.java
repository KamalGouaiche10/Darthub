package it.uniupo.pissir.darts.statistics.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Visibilità di un locale nelle statistiche, alimentata da eventi/locali/conteggio-cambiato: un locale non ancora approvato (o rifiutato) non compare in GET /statistiche/locali/{id} né in GET /statistiche/bersagli/{id}.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class LocaleVisibilityDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code LocaleVisibilityDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public LocaleVisibilityDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Imposta visibile per il componente.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @param visibile indicatore di visibilità della risorsa
     * @see java.sql.Connection
     *  
     */
    public void setVisibile(Connection connection, long localeId, boolean visibile) {
        String sql = "INSERT INTO locale_visibility(locale_id, visibile) VALUES (?, ?) "
                + "ON CONFLICT(locale_id) DO UPDATE SET visibile = excluded.visibile";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, localeId);
            ps.setInt(2, visibile ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Indica se visibile per i dati forniti.
     *
     * @param localeId identificativo del locale
     * @return {@code true} se visibile per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isVisibile(long localeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT visibile FROM locale_visibility WHERE locale_id = ?")) {
                ps.setLong(1, localeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return !rs.next() || rs.getInt("visibile") == 1;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }
}
