package it.uniupo.pissir.darts.game.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.game.model.SensorModalita;
import it.uniupo.pissir.darts.game.model.SensorType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella sensor_types.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class SensorTypeDao extends JdbcDao<SensorType, Long> {

    /**
     * Inizializza {@code SensorTypeDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public SensorTypeDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<SensorType> rowMapper() {
        return rs -> new SensorType(rs.getLong("id"), rs.getLong("game_type_id"), rs.getString("nome"),
                rs.getString("formato_dato"), SensorModalita.valueOf(rs.getString("modalita")));
    }

    /**
     * Cerca il tipo di sensore per identificativo.
     *
     * @param id identificativo del tipo di sensore
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<SensorType> findById(Long id) {
        return queryOne("SELECT * FROM sensor_types WHERE id = ?", id);
    }

    /**
     * Legge una pagina di tipi di sensore.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<SensorType> findAll(Page page) {
        return queryList("SELECT * FROM sensor_types ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Cerca tipi di sensore per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<SensorType> findByGameType(long gameTypeId, Page page) {
        return queryList("SELECT * FROM sensor_types WHERE game_type_id = ? ORDER BY id LIMIT ? OFFSET ?",
                gameTypeId, page.limit(), page.offset());
    }

    /**
     * Conta tipi di sensore secondo per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public long countByGameType(long gameTypeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sensor_types WHERE game_type_id = ?")) {
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
     * Conta tipi di sensore presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM sensor_types")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il tipo di sensore nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param gameTypeId identificativo del gioco
     * @param nome nome della risorsa
     * @param formatoDato formato del dato prodotto dal sensore
     * @param modalita modalità della partita o del sensore
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long gameTypeId, String nome, String formatoDato,
                        SensorModalita modalita) {
        String sql = "INSERT INTO sensor_types(game_type_id, nome, formato_dato, modalita) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, gameTypeId);
            ps.setString(2, nome);
            ps.setString(3, formatoDato);
            ps.setString(4, modalita.name());
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
     * Inserisce il tipo di sensore nel database.
     *
     * @param entity tipo di sensore da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.SensorType} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.SensorType
     *  
     */
    @Override
    public SensorType insert(SensorType entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.gameTypeId(), entity.nome(), entity.formatoDato(), entity.modalita());
            return new SensorType(id, entity.gameTypeId(), entity.nome(), entity.formatoDato(), entity.modalita());
        });
    }

    /**
     * Aggiorna i dati del tipo di sensore nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity tipo di sensore da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, SensorType entity) {
        String sql = "UPDATE sensor_types SET nome = ?, formato_dato = ?, modalita = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.nome());
            ps.setString(2, entity.formatoDato());
            ps.setString(3, entity.modalita().name());
            ps.setLong(4, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Aggiorna i dati del tipo di sensore nel database.
     *
     * @param entity tipo di sensore da salvare
     * @see it.uniupo.pissir.darts.game.model.SensorType
     *  
     */
    @Override
    public void update(SensorType entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il tipo di sensore identificato dalla richiesta.
     *
     * @param id identificativo del tipo di sensore
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM sensor_types WHERE id = ?", id);
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
        if (message != null && message.contains("sensor_types.nome")) {
            return new ConflictException("SENSORE_DUPLICATO", "Esiste già un sensore con questo nome per il gioco");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
