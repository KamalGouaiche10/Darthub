package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.match.model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella teams.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class TeamDao extends JdbcDao<Team, Long> {

    /**
     * Inizializza {@code TeamDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TeamDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Team> rowMapper() {
        return rs -> new Team(rs.getLong("id"), rs.getString("nome"));
    }

    /**
     * Cerca la squadra per identificativo.
     *
     * @param id identificativo della squadra
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Team> findById(Long id) {
        return queryOne("SELECT * FROM teams WHERE id = ?", id);
    }

    /**
     * Legge una pagina di squadre.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Team> findAll(Page page) {
        return queryList("SELECT * FROM teams ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta squadre presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM teams")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce la squadra nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param nome nome della risorsa
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String nome) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO teams(nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
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
     * Inserisce la squadra nel database.
     *
     * @param entity squadra da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Team} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Team
     *  
     */
    @Override
    public Team insert(Team entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.nome());
            return new Team(id, entity.nome());
        });
    }

    /**
     * Aggiorna i dati della squadra nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity squadra da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, Team entity) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE teams SET nome = ? WHERE id = ?")) {
            ps.setString(1, entity.nome());
            ps.setLong(2, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
    }

    /**
     * Aggiorna i dati della squadra nel database.
     *
     * @param entity squadra da salvare
     * @see it.uniupo.pissir.darts.match.model.Team
     *  
     */
    @Override
    public void update(Team entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina la squadra identificato dalla richiesta.
     *
     * @param id identificativo della squadra
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM teams WHERE id = ?", id);
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
        if (message != null && message.contains("teams.nome")) {
            return new ConflictException("SQUADRA_DUPLICATA", "Esiste già una squadra con questo nome");
        }
        return SqlExceptionTranslator.translate(e);
    }
}
