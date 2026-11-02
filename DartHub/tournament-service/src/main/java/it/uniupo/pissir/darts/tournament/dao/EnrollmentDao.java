package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.TipoIscrizione;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella enrollments.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class EnrollmentDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code EnrollmentDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public EnrollmentDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca iscrizioni ai tornei per torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Enrollment> findByTournament(long tournamentId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM enrollments WHERE tournament_id = ? ORDER BY id")) {
                ps.setLong(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return leggiTutti(rs);
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca iscrizioni ai tornei torneo identificativi per partecipante.
     *
     * @param participantId identificativo del partecipante
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findTournamentIdsByParticipant(long participantId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT tournament_id FROM enrollments WHERE participant_id = ? ORDER BY tournament_id")) {
                ps.setLong(1, participantId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> ids = new ArrayList<>();
                    while (rs.next()) ids.add(rs.getLong(1));
                    return ids;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca la iscrizione al torneo usando i dati della richiesta.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<Enrollment> find(long tournamentId, long participantId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM enrollments WHERE tournament_id = ? AND participant_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, participantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Enrollment>empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce la iscrizione al torneo nel database.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @param tipo tipo della risorsa o dell'evento
     * @param dataIscrizione data di iscrizione al torneo
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.tournament.model.TipoIscrizione
     *  
     */
    public long insert(long tournamentId, long participantId, TipoIscrizione tipo, Instant dataIscrizione) {
        return connectionProvider.withTransaction(connection -> {
            try (PreparedStatement deadline = connection.prepareStatement(
                    "SELECT data_inizio, stato FROM tournaments WHERE id = ?")) {
                deadline.setLong(1, tournamentId);
                try (ResultSet rs = deadline.executeQuery()) {
                    if (!rs.next() || !"APERTO".equals(rs.getString("stato"))
                            || !Instant.now().isBefore(Instant.parse(rs.getString("data_inizio")))) {
                        throw new it.uniupo.pissir.darts.common.exception.ConflictException("ISCRIZIONI_CHIUSE",
                                "Le iscrizioni sono chiuse dalla data e ora di inizio del torneo");
                    }
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO enrollments(tournament_id, participant_id, tipo, data_iscrizione) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, participantId);
                ps.setString(3, tipo.name());
                ps.setString(4, dataIscrizione.toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Elimina la iscrizione al torneo identificato dalla richiesta.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     *  
     */
    public void delete(long tournamentId, long participantId) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM enrollments WHERE tournament_id = ? AND participant_id = ?")) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, participantId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Legge tutti per la iscrizione al torneo.
     *
     * @param rs riga corrente del risultato JDBC
     * @return gli elementi trovati o prodotti dall'operazione
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.util.List
     *  
     */
    private List<Enrollment> leggiTutti(ResultSet rs) throws SQLException {
        List<Enrollment> risultato = new ArrayList<>();
        while (rs.next()) {
            risultato.add(map(rs));
        }
        return risultato;
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Enrollment} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.tournament.model.Enrollment
     *  
     */
    private Enrollment map(ResultSet rs) throws SQLException {
        return new Enrollment(rs.getLong("id"), rs.getLong("tournament_id"), rs.getLong("participant_id"),
                TipoIscrizione.valueOf(rs.getString("tipo")), Instant.parse(rs.getString("data_iscrizione")));
    }
}
