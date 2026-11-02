package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.match.model.MatchResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella match_results.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class MatchResultDao extends JdbcDao<MatchResult, Long> {

    /**
     * Inizializza {@code MatchResultDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchResultDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<MatchResult> rowMapper() {
        return rs -> new MatchResult(rs.getLong("id"), rs.getLong("match_id"), rs.getLong("participant_id"),
                rs.getInt("punteggio_finale"), rs.getInt("posizione"), rs.getInt("vincitore") == 1);
    }

    /**
     * Cerca il risultato della partita per identificativo.
     *
     * @param id identificativo del risultato della partita
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<MatchResult> findById(Long id) {
        return queryOne("SELECT * FROM match_results WHERE id = ?", id);
    }

    /**
     * Cerca risultati delle partite per partita.
     *
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchResult> findByMatch(long matchId) {
        return queryList("SELECT * FROM match_results WHERE match_id = ? ORDER BY posizione", matchId);
    }

    /**
     * Verifica l'esistenza di dati associati alla partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @return l'esito della condizione verificata
     * @see java.sql.Connection
     *  
     */
    public boolean existsForMatch(Connection connection, long matchId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM match_results WHERE match_id = ? LIMIT 1")) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Legge una pagina di risultati delle partite.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<MatchResult> findAll(Page page) {
        return queryList("SELECT * FROM match_results ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta risultati delle partite presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM match_results")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il risultato della partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @param punteggioFinale punteggio alla fine della partita
     * @param posizione posizione in classifica
     * @param vincitore partecipante vincitore
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long matchId, long participantId, int punteggioFinale, int posizione,
                        boolean vincitore) {
        String sql = "INSERT INTO match_results(match_id, participant_id, punteggio_finale, posizione, vincitore) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, matchId);
            ps.setLong(2, participantId);
            ps.setInt(3, punteggioFinale);
            ps.setInt(4, posizione);
            ps.setInt(5, vincitore ? 1 : 0);
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
     * Inserisce il risultato della partita nel database.
     *
     * @param entity risultato della partita da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchResult} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchResult
     *  
     */
    @Override
    public MatchResult insert(MatchResult entity) {
        long id = connectionProvider.withTransaction(connection -> insert(connection, entity.matchId(),
                entity.participantId(), entity.punteggioFinale(), entity.posizione(), entity.vincitore()));
        return findById(id).orElseThrow();
    }

    /**
     * Aggiorna i dati del risultato della partita nel database.
     *
     * @param entity risultato della partita da salvare
     * @see it.uniupo.pissir.darts.match.model.MatchResult
     *  
     */
    @Override
    public void update(MatchResult entity) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "UPDATE match_results SET punteggio_finale = ?, posizione = ?, vincitore = ? WHERE id = ?",
                    entity.punteggioFinale(), entity.posizione(), entity.vincitore() ? 1 : 0, entity.id());
            return null;
        });
    }

    /**
     * Elimina il risultato della partita identificato dalla richiesta.
     *
     * @param id identificativo del risultato della partita
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM match_results WHERE id = ?", id);
            return null;
        });
    }
}
