package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella tournaments.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class TournamentDao extends JdbcDao<Tournament, Long> {

    /**
     * Inizializza {@code TournamentDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public TournamentDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Tournament> rowMapper() {
        return rs -> new Tournament(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getLong("game_type_id"),
                ModalitaTorneo.valueOf(rs.getString("modalita")),
                FormulaTorneo.valueOf(rs.getString("formula")),
                rs.getInt("punti_vittoria"),
                rs.getInt("punti_pareggio"),
                rs.getInt("punti_sconfitta"),
                Instant.parse(rs.getString("data_inizio")),
                rs.getString("data_fine_programmata") == null ? null : Instant.parse(rs.getString("data_fine_programmata")),
                TournamentStatus.valueOf(rs.getString("stato")),
                rs.getObject("vincitore_participant_id") != null ? rs.getLong("vincitore_participant_id") : null,
                rs.getString("data_inizio_effettiva") == null ? null : Instant.parse(rs.getString("data_inizio_effettiva")),
                rs.getString("data_fine_effettiva") == null ? null : Instant.parse(rs.getString("data_fine_effettiva")));
    }

    /**
     * Cerca il torneo per identificativo.
     *
     * @param id identificativo del torneo
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Tournament> findById(Long id) {
        return queryOne("SELECT * FROM tournaments WHERE id = ?", id);
    }

    /**
     * Legge una pagina di tornei.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Tournament> findAll(Page page) {
        return queryList("SELECT * FROM tournaments ORDER BY id DESC LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta tornei presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tournaments")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca tornei due identificativi.
     *
     * @param now istante corrente usato nei controlli
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findDueIds(Instant now) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, data_inizio FROM tournaments WHERE stato = 'APERTO' ORDER BY id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                    while (rs.next()) {
                        if (!Instant.parse(rs.getString(2)).isAfter(now)) ids.add(rs.getLong(1));
                    }
                    return ids;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca tornei due in close identificativi.
     *
     * @param now istante corrente usato nei controlli
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findDueToCloseIds(Instant now) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, data_fine_programmata FROM tournaments "
                            + "WHERE stato IN ('APERTO', 'IN_CORSO') AND data_fine_programmata IS NOT NULL ORDER BY id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                    while (rs.next()) {
                        if (!Instant.parse(rs.getString(2)).isAfter(now)) ids.add(rs.getLong(1));
                    }
                    return ids;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Conta le iscrizioni al torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return il valore numerico calcolato o letto
     *  
     */
    public long enrollmentCount(long tournamentId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM enrollments WHERE tournament_id = ?")) {
                ps.setLong(1, tournamentId);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Inserisce il torneo nel database.
     *
     * @param entity torneo da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    @Override
    public Tournament insert(Tournament entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity);
            return new Tournament(id, entity.nome(), entity.gameTypeId(), entity.modalita(), entity.formula(),
                    entity.puntiVittoria(), entity.puntiPareggio(), entity.puntiSconfitta(), entity.dataInizio(),
                    entity.dataFine(), entity.stato(), entity.vincitoreParticipantId(),
                    entity.dataInizioEffettiva(), entity.dataFineEffettiva());
        });
    }

    /**
     * Inserisce il torneo nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity torneo da salvare
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, Tournament entity) {
        String sql = "INSERT INTO tournaments(nome, game_type_id, modalita, formula, punti_vittoria, "
                + "punti_pareggio, punti_sconfitta, data_inizio, data_fine_programmata, stato, vincitore_participant_id, "
                + "data_inizio_effettiva, data_fine_effettiva) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.nome());
            ps.setLong(2, entity.gameTypeId());
            ps.setString(3, entity.modalita().name());
            ps.setString(4, entity.formula().name());
            ps.setInt(5, entity.puntiVittoria());
            ps.setInt(6, entity.puntiPareggio());
            ps.setInt(7, entity.puntiSconfitta());
            ps.setString(8, entity.dataInizio().toString());
            ps.setString(9, entity.dataFine() == null ? null : entity.dataFine().toString());
            ps.setString(10, entity.stato().name());
            ps.setObject(11, entity.vincitoreParticipantId());
            ps.setString(12, entity.dataInizioEffettiva() == null ? null : entity.dataInizioEffettiva().toString());
            ps.setString(13, entity.dataFineEffettiva() == null ? null : entity.dataFineEffettiva().toString());
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
     * Aggiorna i dati del torneo nel database.
     *
     * @param entity torneo da salvare
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    @Override
    public void update(Tournament entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Aggiorna i dati del torneo nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity torneo da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, Tournament entity) {
        String sql = "UPDATE tournaments SET nome = ?, game_type_id = ?, modalita = ?, formula = ?, "
                + "punti_vittoria = ?, punti_pareggio = ?, punti_sconfitta = ?, data_fine_programmata = ?, stato = ?, "
                + "vincitore_participant_id = ?, data_inizio_effettiva = ?, data_fine_effettiva = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.nome());
            ps.setLong(2, entity.gameTypeId());
            ps.setString(3, entity.modalita().name());
            ps.setString(4, entity.formula().name());
            ps.setInt(5, entity.puntiVittoria());
            ps.setInt(6, entity.puntiPareggio());
            ps.setInt(7, entity.puntiSconfitta());
            ps.setString(8, entity.dataFine() == null ? null : entity.dataFine().toString());
            ps.setString(9, entity.stato().name());
            ps.setObject(10, entity.vincitoreParticipantId());
            ps.setString(11, entity.dataInizioEffettiva() == null ? null : entity.dataInizioEffettiva().toString());
            ps.setString(12, entity.dataFineEffettiva() == null ? null : entity.dataFineEffettiva().toString());
            ps.setLong(13, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Elimina il torneo identificato dalla richiesta.
     *
     * @param id identificativo del torneo
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM tournaments WHERE id = ?", id);
            return null;
        });
    }
}
