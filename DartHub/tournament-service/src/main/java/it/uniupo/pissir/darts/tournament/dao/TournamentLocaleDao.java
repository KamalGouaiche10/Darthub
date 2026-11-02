package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO della tabella tournament_locali.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class TournamentLocaleDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code TournamentLocaleDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TournamentLocaleDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca tornei locali identificativi.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findLocaliIds(long tournamentId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT locale_id FROM tournament_locali WHERE tournament_id = ? ORDER BY locale_id")) {
                ps.setLong(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> risultato = new ArrayList<>();
                    while (rs.next()) {
                        risultato.add(rs.getLong("locale_id"));
                    }
                    return risultato;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Indica se associato per i dati forniti.
     *
     * @param tournamentId identificativo del torneo
     * @param localeId identificativo del locale
     * @return {@code true} se associato per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isAssociato(long tournamentId, long localeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM tournament_locali WHERE tournament_id = ? AND locale_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, localeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il torneo nel database.
     *
     * @param tournamentId identificativo del torneo
     * @param localeId identificativo del locale
     *  
     */
    public void insert(long tournamentId, long localeId) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO tournament_locali(tournament_id, locale_id) VALUES (?,?)")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, localeId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Elimina il torneo identificato dalla richiesta.
     *
     * @param tournamentId identificativo del torneo
     * @param localeId identificativo del locale
     *  
     */
    public void delete(long tournamentId, long localeId) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM tournament_locali WHERE tournament_id = ? AND locale_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, localeId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

}
