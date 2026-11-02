package it.uniupo.pissir.darts.game.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.game.model.GameType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella game_types.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class GameTypeDao extends JdbcDao<GameType, Long> {

    /**
     * Inizializza {@code GameTypeDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public GameTypeDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<GameType> rowMapper() {
        return rs -> new GameType(rs.getLong("id"), rs.getString("nome"), rs.getString("descrizione"),
                rs.getString("motore"));
    }

    /**
     * Cerca il gioco per identificativo.
     *
     * @param id identificativo del gioco
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<GameType> findById(Long id) {
        return queryOne("SELECT * FROM game_types WHERE id = ?", id);
    }

    /**
     * Legge una pagina di giochi.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<GameType> findAll(Page page) {
        return queryList("SELECT * FROM game_types ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta giochi presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM game_types")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il gioco nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param nome nome della risorsa
     * @param descrizione descrizione della risorsa
     * @param motore nome del motore delle regole
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String nome, String descrizione, String motore) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO game_types(nome, descrizione, motore) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, descrizione);
            ps.setString(3, motore);
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
     * Inserisce il gioco nel database.
     *
     * @param entity gioco da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.GameType} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.GameType
     *  
     */
    @Override
    public GameType insert(GameType entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.nome(), entity.descrizione(), entity.motore());
            return new GameType(id, entity.nome(), entity.descrizione(), entity.motore());
        });
    }

    /**
     * Aggiorna i dati del gioco nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity gioco da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, GameType entity) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE game_types SET nome = ?, descrizione = ?, motore = ? WHERE id = ?")) {
            ps.setString(1, entity.nome());
            ps.setString(2, entity.descrizione());
            ps.setString(3, entity.motore());
            ps.setLong(4, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Aggiorna i dati del gioco nel database.
     *
     * @param entity gioco da salvare
     * @see it.uniupo.pissir.darts.game.model.GameType
     *  
     */
    @Override
    public void update(GameType entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il gioco identificato dalla richiesta.
     *
     * @param id identificativo del gioco
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM game_types WHERE id = ?", id);
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
        if (message != null && message.contains("game_types.nome")) {
            return new ConflictException("GIOCO_DUPLICATO", "Esiste già un gioco con questo nome");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
