package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella tournament_matches.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class TournamentMatchDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code TournamentMatchDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TournamentMatchDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca tornei per torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<TournamentMatch> findByTournament(long tournamentId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM tournament_matches WHERE tournament_id = ? ORDER BY turno, slot")) {
                ps.setLong(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<TournamentMatch> risultato = new ArrayList<>();
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
     * Cerca tornei per torneo e partita.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<TournamentMatch> findByTournamentAndMatch(long tournamentId, long matchId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM tournament_matches WHERE tournament_id = ? AND match_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<TournamentMatch>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca tornei per partita.
     *
     * @param matchId identificativo della partita
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<TournamentMatch> findByMatch(long matchId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM tournament_matches WHERE match_id = ?")) {
                ps.setLong(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<TournamentMatch>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Verifica se il torneo contiene già partite.
     *
     * @param tournamentId identificativo del torneo
     * @return {@code true} se il torneo contiene già partite; {@code false} altrimenti
     *  
     */
    public boolean esistonoPartite(long tournamentId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM tournament_matches WHERE tournament_id = ? LIMIT 1")) {
                ps.setLong(1, tournamentId);
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
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @see java.sql.Connection
     *  
     */
    public void insert(Connection connection, long tournamentId, long matchId, int turno, Integer slot) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO tournament_matches(tournament_id, match_id, turno, slot) VALUES (?,?,?,?)")) {
            ps.setLong(1, tournamentId);
            ps.setLong(2, matchId);
            ps.setInt(3, turno);
            ps.setObject(4, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce il torneo nel database.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     *  
     */
    public void insert(long tournamentId, long matchId, int turno, Integer slot) {
        connectionProvider.withTransaction(connection -> {
            insert(connection, tournamentId, matchId, turno, slot);
            return null;
        });
    }

    /**
     * Imposta pairing per il componente.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param home partecipante di casa
     * @param away partecipante ospite
     * @param localeId identificativo del locale
     *  
     */
    public void setPairing(long tournamentId, long matchId, long home, long away, long localeId) {
        connectionProvider.withTransaction(connection -> {
            setPairing(connection, tournamentId, matchId, home, away, localeId);
            return null;
        });
    }

    /**
     * Imposta pairing per il componente.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param home partecipante di casa
     * @param away partecipante ospite
     * @param localeId identificativo del locale
     * @see java.sql.Connection
     *  
     */
    public void setPairing(Connection connection, long tournamentId, long matchId, long home, long away, long localeId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tournament_matches SET home_participant_id = ?, "
                + "away_participant_id = ?, locale_id = ? WHERE tournament_id = ? AND match_id = ?")) {
            ps.setLong(1, home);
            ps.setLong(2, away);
            ps.setLong(3, localeId);
            ps.setLong(4, tournamentId);
            ps.setLong(5, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Elimina il torneo identificato dalla richiesta.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     *  
     */
    public void delete(long tournamentId, long matchId) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM tournament_matches WHERE tournament_id = ? AND match_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, matchId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
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
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tournament_matches WHERE tournament_id = ?")) {
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
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.TournamentMatch} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.tournament.model.TournamentMatch
     *  
     */
    private TournamentMatch map(ResultSet rs) throws SQLException {
        return new TournamentMatch(rs.getLong("tournament_id"), rs.getLong("match_id"), rs.getInt("turno"),
                rs.getObject("slot") != null ? rs.getInt("slot") : null,
                rs.getObject("home_participant_id") == null ? null : rs.getLong("home_participant_id"),
                rs.getObject("away_participant_id") == null ? null : rs.getLong("away_participant_id"),
                rs.getObject("locale_id") == null ? null : rs.getLong("locale_id"));
    }
}
