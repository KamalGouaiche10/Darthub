package it.uniupo.pissir.darts.user.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;

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
 * DAO della tabella users.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class UserDao extends JdbcDao<User, Long> {

    /**
     * Inizializza {@code UserDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public UserDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<User> rowMapper() {
        return rs -> new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                Role.valueOf(rs.getString("role")),
                rs.getInt("enabled") == 1,
                ApprovalStatus.valueOf(rs.getString("approval_status")),
                Instant.parse(rs.getString("created_at")),
                rs.getString("first_name"),
                rs.getString("last_name"));
    }

    /**
     * Cerca l'utente per identificativo.
     *
     * @param id identificativo dell'utente
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<User> findById(Long id) {
        return queryOne("SELECT * FROM users WHERE id = ?", id);
    }

    /**
     * Cerca utenti per username.
     *
     * @param username nome dell'account
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<User> findByUsername(String username) {
        return queryOne("SELECT * FROM users WHERE username = ?", username);
    }

    /**
     * Cerca utenti per email.
     *
     * @param email indirizzo email dell'account
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<User> findByEmail(String email) {
        return queryOne("SELECT * FROM users WHERE email = ? COLLATE NOCASE", email);
    }

    /**
     * Legge una pagina di utenti.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<User> findAll(Page page) {
        return queryList("SELECT * FROM users ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param roleFilter filtro per ruolo dell'utente
     * @param approvalStatusFilter filtro per stato di approvazione
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<User> search(Role roleFilter, ApprovalStatus approvalStatusFilter, Page page) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (roleFilter != null) {
            sql.append(" AND role = ?");
            params.add(roleFilter.name());
        }
        if (approvalStatusFilter != null) {
            sql.append(" AND approval_status = ?");
            params.add(approvalStatusFilter.name());
        }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        params.add(page.limit());
        params.add(page.offset());
        return queryList(sql.toString(), params.toArray());
    }

    /**
     * Conta utenti presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Conta utenti secondo search.
     *
     * @param roleFilter filtro per ruolo dell'utente
     * @param approvalStatusFilter filtro per stato di approvazione
     * @return il numero di elementi corrispondenti ai criteri
     * @see it.uniupo.pissir.darts.user.model.Role
     *  
     */
    public long countSearch(Role roleFilter, ApprovalStatus approvalStatusFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (roleFilter != null) {
            sql.append(" AND role = ?");
            params.add(roleFilter.name());
        }
        if (approvalStatusFilter != null) {
            sql.append(" AND approval_status = ?");
            params.add(approvalStatusFilter.name());
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
     * Inserisce l'utente nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param passwordHash hash della password
     * @param salt sale usato nel calcolo dell'hash
     * @param role ruolo dell'utente
     * @param approvalStatus stato di approvazione
     * @param createdAt istante di creazione
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String username, String email, String passwordHash, String salt,
                        Role role, ApprovalStatus approvalStatus, Instant createdAt) {
        return insert(connection, username, email, passwordHash, salt, role, approvalStatus, createdAt, null, null);
    }

    /**
     * Inserisce l'utente nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param passwordHash hash della password
     * @param salt sale usato nel calcolo dell'hash
     * @param role ruolo dell'utente
     * @param approvalStatus stato di approvazione
     * @param createdAt istante di creazione
     * @param firstName nome anagrafico dell'utente
     * @param lastName cognome dell'utente
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String username, String email, String passwordHash, String salt,
                       Role role, ApprovalStatus approvalStatus, Instant createdAt, String firstName, String lastName) {
        String sql = "INSERT INTO users(username, email, password_hash, salt, role, enabled, approval_status, "
                + "created_at, first_name, last_name) VALUES (?,?,?,?,?,1,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.setString(5, role.name());
            ps.setString(6, approvalStatus.name());
            ps.setString(7, createdAt.toString());
            ps.setString(8, firstName);
            ps.setString(9, lastName);
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
     * Inserisce l'utente nel database.
     *
     * @param entity utente da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.User} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    @Override
    public User insert(User entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.username(), entity.email(), entity.passwordHash(),
                    entity.salt(), entity.role(), entity.approvalStatus(), entity.createdAt(),
                    entity.firstName(), entity.lastName());
            return new User(id, entity.username(), entity.email(), entity.passwordHash(), entity.salt(),
                    entity.role(), true, entity.approvalStatus(), entity.createdAt(),
                    entity.firstName(), entity.lastName());
        });
    }

    /**
     * Aggiorna i dati dell'utente nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity utente da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, User entity) {
        String sql = "UPDATE users SET email = ?, password_hash = ?, salt = ?, role = ?, enabled = ?, "
                + "approval_status = ?, first_name = ?, last_name = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.email());
            ps.setString(2, entity.passwordHash());
            ps.setString(3, entity.salt());
            ps.setString(4, entity.role().name());
            ps.setInt(5, entity.enabled() ? 1 : 0);
            ps.setString(6, entity.approvalStatus().name());
            ps.setString(7, entity.firstName());
            ps.setString(8, entity.lastName());
            ps.setLong(9, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Aggiorna i dati dell'utente nel database.
     *
     * @param entity utente da salvare
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    @Override
    public void update(User entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina l'utente identificato dalla richiesta.
     *
     * @param id identificativo dell'utente
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM users WHERE id = ?", id);
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
        if (message != null && message.contains("users.username")) {
            return new ConflictException("USERNAME_DUPLICATO", "Username già in uso");
        }
        if (message != null && message.contains("users.email")) {
            return new ConflictException("EMAIL_DUPLICATA", "Email già in uso");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
