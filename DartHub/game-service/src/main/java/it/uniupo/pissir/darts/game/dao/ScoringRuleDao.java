package it.uniupo.pissir.darts.game.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella scoring_rules.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class ScoringRuleDao extends JdbcDao<ScoringRule, Long> {

    /**
     * Inizializza {@code ScoringRuleDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public ScoringRuleDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<ScoringRule> rowMapper() {
        return rs -> new ScoringRule(
                rs.getLong("id"),
                rs.getLong("game_type_id"),
                rs.getString("variante"),
                Direzione.valueOf(rs.getString("direzione")),
                rs.getInt("punteggio_iniziale"),
                rs.getInt("punteggio_obiettivo"),
                rs.getInt("chiusura_double") == 1,
                rs.getInt("bust_annulla_turno") == 1,
                rs.getInt("max_tiri_per_turno"),
                rs.getString("parametri_json"));
    }

    /**
     * Cerca la regola di punteggio per identificativo.
     *
     * @param id identificativo della regola di punteggio
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<ScoringRule> findById(Long id) {
        return queryOne("SELECT * FROM scoring_rules WHERE id = ?", id);
    }

    /**
     * Legge una pagina di regole di punteggio.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<ScoringRule> findAll(Page page) {
        return queryList("SELECT * FROM scoring_rules ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Cerca regole di punteggio per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<ScoringRule> findByGameType(long gameTypeId, Page page) {
        return queryList("SELECT * FROM scoring_rules WHERE game_type_id = ? ORDER BY id LIMIT ? OFFSET ?",
                gameTypeId, page.limit(), page.offset());
    }

    /**
     * Conta regole di punteggio secondo per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public long countByGameType(long gameTypeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM scoring_rules WHERE game_type_id = ?")) {
                ps.setLong(1, gameTypeId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Conta regole di punteggio presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM scoring_rules")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce la regola di punteggio nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param gameTypeId identificativo del gioco
     * @param variante nome della variante del gioco
     * @param direzione direzione di variazione del punteggio
     * @param punteggioIniziale punteggio iniziale previsto dalla regola
     * @param punteggioObiettivo punteggio da raggiungere per vincere
     * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
     * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
     * @param maxTiriPerTurno numero massimo di tiri per turno
     * @param parametriJson parametri aggiuntivi della regola in JSON
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long gameTypeId, String variante, Direzione direzione,
                        int punteggioIniziale, int punteggioObiettivo, boolean chiusuraDouble,
                        boolean bustAnnullaTurno, int maxTiriPerTurno, String parametriJson) {
        String sql = "INSERT INTO scoring_rules(game_type_id, variante, direzione, punteggio_iniziale, "
                + "punteggio_obiettivo, chiusura_double, bust_annulla_turno, max_tiri_per_turno, parametri_json) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, gameTypeId);
            ps.setString(2, variante);
            ps.setString(3, direzione.name());
            ps.setInt(4, punteggioIniziale);
            ps.setInt(5, punteggioObiettivo);
            ps.setInt(6, chiusuraDouble ? 1 : 0);
            ps.setInt(7, bustAnnullaTurno ? 1 : 0);
            ps.setInt(8, maxTiriPerTurno);
            ps.setString(9, parametriJson);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Inserisce la regola di punteggio nel database.
     *
     * @param entity regola di punteggio da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    @Override
    public ScoringRule insert(ScoringRule entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.gameTypeId(), entity.variante(), entity.direzione(),
                    entity.punteggioIniziale(), entity.punteggioObiettivo(), entity.chiusuraDouble(),
                    entity.bustAnnullaTurno(), entity.maxTiriPerTurno(), entity.parametriJson());
            return new ScoringRule(id, entity.gameTypeId(), entity.variante(), entity.direzione(),
                    entity.punteggioIniziale(), entity.punteggioObiettivo(), entity.chiusuraDouble(),
                    entity.bustAnnullaTurno(), entity.maxTiriPerTurno(), entity.parametriJson());
        });
    }

    /**
     * Aggiorna i dati della regola di punteggio nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity regola di punteggio da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, ScoringRule entity) {
        String sql = "UPDATE scoring_rules SET variante = ?, direzione = ?, punteggio_iniziale = ?, "
                + "punteggio_obiettivo = ?, chiusura_double = ?, bust_annulla_turno = ?, max_tiri_per_turno = ?, "
                + "parametri_json = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.variante());
            ps.setString(2, entity.direzione().name());
            ps.setInt(3, entity.punteggioIniziale());
            ps.setInt(4, entity.punteggioObiettivo());
            ps.setInt(5, entity.chiusuraDouble() ? 1 : 0);
            ps.setInt(6, entity.bustAnnullaTurno() ? 1 : 0);
            ps.setInt(7, entity.maxTiriPerTurno());
            ps.setString(8, entity.parametriJson());
            ps.setLong(9, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Aggiorna i dati della regola di punteggio nel database.
     *
     * @param entity regola di punteggio da salvare
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    @Override
    public void update(ScoringRule entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina la regola di punteggio identificato dalla richiesta.
     *
     * @param id identificativo della regola di punteggio
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM scoring_rules WHERE id = ?", id);
            return null;
        });
    }

    /**
     * Converte un conflitto di unicità nell'errore applicativo previsto.
     *
     * @param e eccezione da gestire
     * @return l'oggetto {@code RuntimeException} prodotto o recuperato
     * @see java.sql.SQLException
     *  
     */
    private RuntimeException translateDuplicate(SQLException e) {
        String message = e.getMessage();
        if (message != null && message.contains("scoring_rules.variante")) {
            return new ConflictException("REGOLA_DUPLICATA", "Esiste già una regola con questa variante per il gioco");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
