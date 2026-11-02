package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.match.model.MatchParticipant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella match_participants.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class MatchParticipantDao extends JdbcDao<MatchParticipant, Long> {

    /**
     * Inizializza {@code MatchParticipantDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchParticipantDao(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    /**
     * Fornisce il convertitore delle righe JDBC nel tipo gestito dal DAO.
     *
     * @return il convertitore delle righe JDBC nel tipo dell'entità
     * @see it.uniupo.pissir.darts.common.persistence.RowMapper
     *  
     */
    @Override
    protected RowMapper<MatchParticipant> rowMapper() {
        return rs -> new MatchParticipant(
                rs.getLong("id"),
                rs.getLong("match_id"),

                rs.getObject("player_id") != null ? rs.getLong("player_id") : null,
                rs.getObject("team_id") != null ? rs.getLong("team_id") : null,
                rs.getInt("ordine_turno"),
                rs.getInt("punteggio_corrente"),
                rs.getInt("ritirato") == 1);
    }

    /**
     * Cerca il partecipante alla partita per identificativo.
     *
     * @param id identificativo del partecipante alla partita
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<MatchParticipant> findById(Long id) {
        return queryOne("SELECT * FROM match_participants WHERE id = ?", id);
    }

    /**
     * Cerca partecipanti alla partita per partita.
     *
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchParticipant> findByMatch(long matchId) {
        return queryList("SELECT * FROM match_participants WHERE match_id = ? ORDER BY ordine_turno", matchId);
    }

    /**
     * Cerca partecipanti alla partita attivi per partita.
     *
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchParticipant> findAttiviByMatch(long matchId) {
        return queryList("SELECT * FROM match_participants WHERE match_id = ? AND ritirato = 0 ORDER BY ordine_turno",
                matchId);
    }

    /**
     * Cerca partecipanti alla partita per partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchParticipant> findByMatch(Connection connection, long matchId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM match_participants WHERE match_id = ? ORDER BY ordine_turno")) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MatchParticipant> risultato = new java.util.ArrayList<>();
                RowMapper<MatchParticipant> mapper = rowMapper();
                while (rs.next()) {
                    risultato.add(mapper.map(rs));
                }
                return risultato;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Legge una pagina di partecipanti alla partita.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<MatchParticipant> findAll(Page page) {
        return queryList("SELECT * FROM match_participants ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta partecipanti alla partita presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM match_participants")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Calcola la posizione successiva nell'ordine dei partecipanti.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public int prossimoOrdineTurno(Connection connection, long matchId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(ordine_turno), 0) + 1 FROM match_participants WHERE match_id = ?")) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce il partecipante alla partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @param ordineTurno posizione del partecipante nell'ordine di gioco
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long matchId, Long playerId, Long teamId, int ordineTurno) {
        String sql = "INSERT INTO match_participants(match_id, player_id, team_id, ordine_turno, "
                + "punteggio_corrente, ritirato) VALUES (?,?,?,?,0,0)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, matchId);
            ps.setObject(2, playerId);
            ps.setObject(3, teamId);
            ps.setInt(4, ordineTurno);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce il partecipante alla partita nel database.
     *
     * @param entity partecipante alla partita da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    @Override
    public MatchParticipant insert(MatchParticipant entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.matchId(), entity.playerId(), entity.teamId(), entity.ordineTurno());

            return new MatchParticipant(id, entity.matchId(), entity.playerId(), entity.teamId(), entity.ordineTurno(), 0, false);
        });
    }

    /**
     * Aggiorna i dati del partecipante alla partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity partecipante alla partita da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, MatchParticipant entity) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE match_participants SET punteggio_corrente = ?, ritirato = ? WHERE id = ?")) {
            ps.setInt(1, entity.punteggioCorrente());
            ps.setInt(2, entity.ritirato() ? 1 : 0);
            ps.setLong(3, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna i dati del partecipante alla partita nel database.
     *
     * @param entity partecipante alla partita da salvare
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    @Override
    public void update(MatchParticipant entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il partecipante alla partita identificato dalla richiesta.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param id identificativo del partecipante alla partita
     * @see java.sql.Connection
     *  
     */
    public void delete(Connection connection, long id) {
        executeUpdate(connection, "DELETE FROM match_participants WHERE id = ?", id);
    }

    /**
     * Elimina il partecipante alla partita identificato dalla richiesta.
     *
     * @param id identificativo del partecipante alla partita
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            delete(connection, id);
            return null;
        });
    }
}
