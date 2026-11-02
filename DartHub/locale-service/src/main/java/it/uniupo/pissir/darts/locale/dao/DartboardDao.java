package it.uniupo.pissir.darts.locale.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.DartboardStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella bersagli.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class DartboardDao extends JdbcDao<Dartboard, Long> {

    /**
     * Inizializza {@code DartboardDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public DartboardDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Dartboard> rowMapper() {
        return rs -> new Dartboard(
                rs.getLong("id"),
                rs.getLong("locale_id"),
                rs.getString("identificativo_locale"),
                rs.getLong("game_type_id"),
                DartboardStatus.valueOf(rs.getString("stato")),
                Instant.parse(rs.getString("created_at")));
    }

    /**
     * Cerca il bersaglio per identificativo.
     *
     * @param id identificativo del bersaglio
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Dartboard> findById(Long id) {
        return queryOne("SELECT * FROM bersagli WHERE id = ?", id);
    }

    /**
     * Legge una pagina di bersagli.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Dartboard> findAll(Page page) {
        return queryList("SELECT * FROM bersagli ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Cerca bersagli per locale.
     *
     * @param localeId identificativo del locale
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Dartboard> findByLocale(long localeId, Page page) {
        return queryList("SELECT * FROM bersagli WHERE locale_id = ? ORDER BY id LIMIT ? OFFSET ?",
                localeId, page.limit(), page.offset());
    }

    /**
     * Conta bersagli secondo per locale.
     *
     * @param localeId identificativo del locale
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public long countByLocale(long localeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM bersagli WHERE locale_id = ?")) {
                ps.setLong(1, localeId);
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
     * Conta bersagli presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM bersagli")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il bersaglio nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @param identificativoLocale identificativo del bersaglio nel locale
     * @param gameTypeId identificativo del gioco
     * @param stato stato da impostare o verificare
     * @param createdAt istante di creazione
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long localeId, String identificativoLocale, long gameTypeId,
                        DartboardStatus stato, Instant createdAt) {
        String sql = "INSERT INTO bersagli(locale_id, identificativo_locale, game_type_id, stato, created_at) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, localeId);
            ps.setString(2, identificativoLocale);
            ps.setLong(3, gameTypeId);
            ps.setString(4, stato.name());
            ps.setString(5, createdAt.toString());
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
     * Inserisce il bersaglio nel database.
     *
     * @param entity bersaglio da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Dartboard} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Dartboard
     *  
     */
    @Override
    public Dartboard insert(Dartboard entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.localeId(), entity.identificativoLocale(), entity.gameTypeId(),
                    entity.stato(), entity.createdAt());
            return new Dartboard(id, entity.localeId(), entity.identificativoLocale(), entity.gameTypeId(),
                    entity.stato(), entity.createdAt());
        });
    }

    /**
     * Aggiorna i dati del bersaglio nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity bersaglio da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, Dartboard entity) {
        String sql = "UPDATE bersagli SET identificativo_locale = ?, game_type_id = ?, stato = ?, "
                + "offline_due_to_edge = 0 WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.identificativoLocale());
            ps.setLong(2, entity.gameTypeId());
            ps.setString(3, entity.stato().name());
            ps.setLong(4, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Occupa il bersaglio soltanto se risulta disponibile.
     *
     * @param id identificativo del bersaglio
     * @return l'esito della condizione verificata
     *  
     */
    public boolean markInUseIfAvailable(long id) {
        return connectionProvider.withTransaction(connection ->
                executeUpdate(connection, "UPDATE bersagli SET stato = 'IN_USO' WHERE id = ? AND stato = 'DISPONIBILE'", id) == 1);
    }

    /**
     * Segna offline il bersaglio soltanto se non è occupato.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param id identificativo del bersaglio
     * @see java.sql.Connection
     *  
     */
    public void markOfflineIfAvailable(Connection connection, long id) {
        executeUpdate(connection, "UPDATE bersagli SET stato = 'OFFLINE', offline_due_to_edge = 1 "
                + "WHERE id = ? AND stato = 'DISPONIBILE'", id);
    }

    /**
     * Ripristina la disponibilità dei bersagli collegati a edge online.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @see java.sql.Connection
     *  
     */
    public void restoreAvailableForOnlineEdges(Connection connection, long localeId) {
        executeUpdate(connection, "UPDATE bersagli SET stato = 'DISPONIBILE', offline_due_to_edge = 0 "
                + "WHERE locale_id = ? AND stato = 'OFFLINE' AND offline_due_to_edge = 1 AND EXISTS ("
                + "SELECT 1 FROM edge_devices e WHERE e.dartboard_id = bersagli.id AND e.stato = 'ONLINE')", localeId);
    }

    /**
     * Aggiorna i dati del bersaglio nel database.
     *
     * @param entity bersaglio da salvare
     * @see it.uniupo.pissir.darts.locale.model.Dartboard
     *  
     */
    @Override
    public void update(Dartboard entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il bersaglio identificato dalla richiesta.
     *
     * @param id identificativo del bersaglio
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM bersagli WHERE id = ?", id);
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
        if (message != null && message.contains("bersagli.identificativo_locale")) {
            return new ConflictException("DARTBOARD_DUPLICATO", "Identificativo già presente nel locale");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
