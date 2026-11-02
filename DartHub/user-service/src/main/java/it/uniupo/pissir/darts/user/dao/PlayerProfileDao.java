package it.uniupo.pissir.darts.user.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.user.model.PlayerProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella player_profiles, chiave primaria = user_id.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class PlayerProfileDao extends JdbcDao<PlayerProfile, Long> {

    /**
     * Inizializza {@code PlayerProfileDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public PlayerProfileDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<PlayerProfile> rowMapper() {
        return rs -> new PlayerProfile(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("avatar_url"));
    }

    /**
     * Cerca il profilo del giocatore per identificativo.
     *
     * @param userId identificativo dell'utente
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<PlayerProfile> findById(Long userId) {
        return queryOne("SELECT * FROM player_profiles WHERE user_id = ?", userId);
    }

    /**
     * Legge una pagina di profili dei giocatori.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<PlayerProfile> findAll(Page page) {
        return queryList("SELECT * FROM player_profiles ORDER BY user_id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta profili dei giocatori presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM player_profiles")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il profilo del giocatore nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param userId identificativo dell'utente
     * @param nickname nome pubblico del giocatore
     * @param avatarUrl indirizzo dell'immagine del profilo
     * @see java.sql.Connection
     *  
     */
    public void insert(Connection connection, long userId, String nickname, String avatarUrl) {
        executeUpdate(connection, "INSERT INTO player_profiles(user_id, nickname, avatar_url) VALUES (?,?,?)",
                userId, nickname, avatarUrl);
    }

    /**
     * Inserisce il profilo del giocatore nel database.
     *
     * @param entity profilo del giocatore da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.PlayerProfile} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.PlayerProfile
     *  
     */
    @Override
    public PlayerProfile insert(PlayerProfile entity) {
        connectionProvider.withTransaction(connection -> {
            insert(connection, entity.userId(), entity.nickname(), entity.avatarUrl());
            return null;
        });
        return entity;
    }

    /**
     * Aggiorna i dati del profilo del giocatore nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param userId identificativo dell'utente
     * @param nickname nome pubblico del giocatore
     * @param avatarUrl indirizzo dell'immagine del profilo
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, long userId, String nickname, String avatarUrl) {
        executeUpdate(connection, "UPDATE player_profiles SET nickname = ?, avatar_url = ? WHERE user_id = ?",
                nickname, avatarUrl, userId);
    }

    /**
     * Aggiorna i dati del profilo del giocatore nel database.
     *
     * @param entity profilo del giocatore da salvare
     * @see it.uniupo.pissir.darts.user.model.PlayerProfile
     *  
     */
    @Override
    public void update(PlayerProfile entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity.userId(), entity.nickname(), entity.avatarUrl());
            return null;
        });
    }

    /**
     * Elimina il profilo del giocatore identificato dalla richiesta.
     *
     * @param userId identificativo dell'utente
     *  
     */
    @Override
    public void delete(Long userId) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM player_profiles WHERE user_id = ?", userId);
            return null;
        });
    }
}
