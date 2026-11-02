package it.uniupo.pissir.darts.locale.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella locali.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class LocaleDao extends JdbcDao<Locale, Long> {

    /**
     * Inizializza {@code LocaleDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public LocaleDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Locale> rowMapper() {
        return rs -> new Locale(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("indirizzo"),
                LocaleType.valueOf(rs.getString("tipo")),
                rs.getLong("owner_user_id"),
                LocaleStatus.valueOf(rs.getString("stato")),
                Instant.parse(rs.getString("created_at")));
    }

    /**
     * Cerca il locale per identificativo.
     *
     * @param id identificativo del locale
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Locale> findById(Long id) {
        return queryOne("SELECT * FROM locali WHERE id = ?", id);
    }

    /**
     * Legge una pagina di locali.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Locale> findAll(Page page) {
        return queryList("SELECT * FROM locali ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param ownerUserIdFilter filtro per identificativo del proprietario del locale
     * @param statoFilter filtro per stato da impostare o verificare
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Locale> search(Long ownerUserIdFilter, LocaleStatus statoFilter, Page page) {
        StringBuilder sql = new StringBuilder("SELECT * FROM locali WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (ownerUserIdFilter != null) {
            sql.append(" AND owner_user_id = ?");
            params.add(ownerUserIdFilter);
        }
        if (statoFilter != null) {
            sql.append(" AND stato = ?");
            params.add(statoFilter.name());
        }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        params.add(page.limit());
        params.add(page.offset());
        return queryList(sql.toString(), params.toArray());
    }

    /**
     * Conta locali secondo search.
     *
     * @param ownerUserIdFilter filtro per identificativo del proprietario del locale
     * @param statoFilter filtro per stato da impostare o verificare
     * @return il numero di elementi corrispondenti ai criteri
     * @see it.uniupo.pissir.darts.locale.model.LocaleStatus
     *  
     */
    public long countSearch(Long ownerUserIdFilter, LocaleStatus statoFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM locali WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (ownerUserIdFilter != null) {
            sql.append(" AND owner_user_id = ?");
            params.add(ownerUserIdFilter);
        }
        if (statoFilter != null) {
            sql.append(" AND stato = ?");
            params.add(statoFilter.name());
        }
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
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
     * Conta locali presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM locali")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il locale nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param nome nome della risorsa
     * @param indirizzo indirizzo del locale
     * @param tipo tipo della risorsa o dell'evento
     * @param ownerUserId identificativo del proprietario del locale
     * @param stato stato da impostare o verificare
     * @param createdAt istante di creazione
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String nome, String indirizzo, LocaleType tipo, long ownerUserId,
                        LocaleStatus stato, Instant createdAt) {
        String sql = "INSERT INTO locali(nome, indirizzo, tipo, owner_user_id, stato, created_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, indirizzo);
            ps.setString(3, tipo.name());
            ps.setLong(4, ownerUserId);
            ps.setString(5, stato.name());
            ps.setString(6, createdAt.toString());
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
     * Inserisce il locale nel database.
     *
     * @param entity locale da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    @Override
    public Locale insert(Locale entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.nome(), entity.indirizzo(), entity.tipo(), entity.ownerUserId(),
                    entity.stato(), entity.createdAt());
            return new Locale(id, entity.nome(), entity.indirizzo(), entity.tipo(), entity.ownerUserId(),
                    entity.stato(), entity.createdAt());
        });
    }

    /**
     * Aggiorna i dati del locale nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity locale da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, Locale entity) {
        String sql = "UPDATE locali SET nome = ?, indirizzo = ?, tipo = ?, stato = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.nome());
            ps.setString(2, entity.indirizzo());
            ps.setString(3, entity.tipo().name());
            ps.setString(4, entity.stato().name());
            ps.setLong(5, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna i dati del locale nel database.
     *
     * @param entity locale da salvare
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    @Override
    public void update(Locale entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il locale identificato dalla richiesta.
     *
     * @param id identificativo del locale
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM locali WHERE id = ?", id);
            return null;
        });
    }
}
