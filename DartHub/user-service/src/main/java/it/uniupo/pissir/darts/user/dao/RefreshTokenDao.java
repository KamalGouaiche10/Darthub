package it.uniupo.pissir.darts.user.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.user.model.RefreshToken;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella refresh_tokens: token_hash è sempre un digest, mai il token in chiaro.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class RefreshTokenDao extends JdbcDao<RefreshToken, Long> {

    /**
     * Inizializza {@code RefreshTokenDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public RefreshTokenDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<RefreshToken> rowMapper() {
        return rs -> new RefreshToken(rs.getLong("id"), rs.getLong("user_id"), rs.getString("token_hash"),
                Instant.parse(rs.getString("expires_at")), rs.getInt("revoked") == 1);
    }

    /**
     * Cerca il token di aggiornamento per identificativo.
     *
     * @param id identificativo del token di aggiornamento
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<RefreshToken> findById(Long id) {
        return queryOne("SELECT * FROM refresh_tokens WHERE id = ?", id);
    }

    /**
     * Cerca token di aggiornamento per token hash.
     *
     * @param tokenHash hash del token
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return queryOne("SELECT * FROM refresh_tokens WHERE token_hash = ?", tokenHash);
    }

    /**
     * Legge una pagina di token di aggiornamento.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<RefreshToken> findAll(Page page) {
        return queryList("SELECT * FROM refresh_tokens ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta token di aggiornamento presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM refresh_tokens")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il token di aggiornamento nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param userId identificativo dell'utente
     * @param tokenHash hash del token
     * @param expiresAt istante di scadenza
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long userId, String tokenHash, Instant expiresAt) {
        return executeInsertReturningId(connection,
                "INSERT INTO refresh_tokens(user_id, token_hash, expires_at, revoked) VALUES (?,?,?,0)",
                userId, tokenHash, expiresAt.toString());
    }

    /**
     * Inserisce il token di aggiornamento nel database.
     *
     * @param entity token di aggiornamento da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.RefreshToken} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.RefreshToken
     *  
     */
    @Override
    public RefreshToken insert(RefreshToken entity) {
        long id = connectionProvider.withTransaction(connection ->
                insert(connection, entity.userId(), entity.tokenHash(), entity.expiresAt()));
        return new RefreshToken(id, entity.userId(), entity.tokenHash(), entity.expiresAt(), false);
    }

    /**
     * Revoca il token o le credenziali indicati.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param id identificativo del token di aggiornamento
     * @see java.sql.Connection
     *  
     */
    public void revoke(Connection connection, long id) {
        executeUpdate(connection, "UPDATE refresh_tokens SET revoked = 1 WHERE id = ?", id);
    }

    /**
     * Aggiorna i dati del token di aggiornamento nel database.
     *
     * @param entity token di aggiornamento da salvare
     * @see it.uniupo.pissir.darts.user.model.RefreshToken
     *  
     */
    @Override
    public void update(RefreshToken entity) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "UPDATE refresh_tokens SET revoked = ? WHERE id = ?",
                    entity.revoked() ? 1 : 0, entity.id());
            return null;
        });
    }

    /**
     * Elimina il token di aggiornamento identificato dalla richiesta.
     *
     * @param id identificativo del token di aggiornamento
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM refresh_tokens WHERE id = ?", id);
            return null;
        });
    }
}
