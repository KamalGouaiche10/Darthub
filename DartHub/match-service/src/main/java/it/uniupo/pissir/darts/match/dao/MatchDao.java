package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;

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
 * DAO della tabella matches.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class MatchDao extends JdbcDao<Match, Long> {

    /**
     * Inizializza {@code MatchDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Match> rowMapper() {
        return rs -> new Match(
                rs.getLong("id"),
                rs.getLong("dartboard_id"),
                rs.getLong("locale_id"),
                rs.getLong("game_type_id"),
                rs.getLong("scoring_rule_id"),
                ModalitaPartita.valueOf(rs.getString("modalita")),
                MatchStatus.valueOf(rs.getString("stato")),
                rs.getLong("created_by"),
                rs.getString("local_uuid"),
                rs.getLong("clock_offset_ms"),
                parseInstant(rs.getString("data_inizio")),
                parseInstant(rs.getString("data_fine")));
    }

    /**
     * Legge e converte instant dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
     * @see java.time.Instant
     *  
     */
    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    /**
     * Cerca la partita per identificativo.
     *
     * @param id identificativo della partita
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Match> findById(Long id) {
        return queryOne("SELECT * FROM matches WHERE id = ?", id);
    }

    /**
     * Cerca partite per locale uuid.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<Match> findByLocalUuid(String localUuid) {
        return queryOne("SELECT * FROM matches WHERE local_uuid = ?", localUuid);
    }

    /**
     * Indica se sensore torneo per i dati forniti.
     *
     * @param matchId identificativo della partita
     * @return {@code true} se sensore torneo per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isSensorTournament(long matchId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM sensor_tournament_matches WHERE match_id = ?")) {
                ps.setLong(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Indica se sensore avviata per i dati forniti.
     *
     * @param matchId identificativo della partita
     * @return {@code true} se sensore avviata per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isSensorStarted(long matchId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT started FROM sensor_tournament_matches WHERE match_id = ?")) {
                ps.setLong(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Registra lo stato sensore torneo.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @see java.sql.Connection
     *  
     */
    public void markSensorTournament(Connection connection, long matchId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO sensor_tournament_matches(match_id, started) VALUES (?, 0)")) {
            ps.setLong(1, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Registra lo stato sensore avviata.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @see java.sql.Connection
     *  
     */
    public void markSensorStarted(Connection connection, long matchId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sensor_tournament_matches SET started = 1 WHERE match_id = ?")) {
            ps.setLong(1, matchId);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Partita di torneo senza bersaglio assegnato");
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Legge una pagina di partite.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Match> findAll(Page page) {
        return queryList("SELECT * FROM matches ORDER BY id DESC LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param playerId identificativo del giocatore
     * @param localeId identificativo del locale
     * @param stato stato da impostare o verificare
     * @param dal limite temporale iniziale
     * @param al limite temporale finale
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Match> search(Long playerId, Long localeId, MatchStatus stato, Instant dal, Instant al, Page page) {
        Filtro filtro = costruisciFiltro(playerId, localeId, stato, dal, al);
        String sql = "SELECT DISTINCT m.* FROM matches m" + filtro.join() + filtro.where()
                + " ORDER BY m.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(filtro.params());
        params.add(page.limit());
        params.add(page.offset());
        return queryList(sql, params.toArray());
    }

    /**
     * Conta partite secondo search.
     *
     * @param playerId identificativo del giocatore
     * @param localeId identificativo del locale
     * @param stato stato da impostare o verificare
     * @param dal limite temporale iniziale
     * @param al limite temporale finale
     * @return il numero di elementi corrispondenti ai criteri
     * @see it.uniupo.pissir.darts.match.model.MatchStatus
     *  
     */
    public long countSearch(Long playerId, Long localeId, MatchStatus stato, Instant dal, Instant al) {
        Filtro filtro = costruisciFiltro(playerId, localeId, stato, dal, al);
        String sql = "SELECT COUNT(DISTINCT m.id) FROM matches m" + filtro.join() + filtro.where();
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, filtro.params());
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
     * Conta partite presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM matches")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce la partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleId identificativo della regola di punteggio
     * @param modalita modalità della partita o del sensore
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @param localUuid identificativo univoco della partita creata offline
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                        ModalitaPartita modalita, long createdBy, String localUuid) {
        String sql = "INSERT INTO matches(dartboard_id, locale_id, game_type_id, scoring_rule_id, modalita, "
                + "stato, created_by, local_uuid, clock_offset_ms) VALUES (?,?,?,?,?,'CREATA',?,?,0)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, dartboardId);
            ps.setLong(2, localeId);
            ps.setLong(3, gameTypeId);
            ps.setLong(4, scoringRuleId);
            ps.setString(5, modalita.name());
            ps.setLong(6, createdBy);
            ps.setString(7, localUuid);
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
     * Inserisce la partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleId identificativo della regola di punteggio
     * @param modalita modalità della partita o del sensore
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @param localUuid identificativo univoco della partita creata offline
     * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
     * @param istante istante da elaborare
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insertOffline(Connection connection, long dartboardId, long localeId, long gameTypeId,
                               long scoringRuleId, ModalitaPartita modalita, long createdBy, String localUuid,
                               long clockOffsetMs, Instant istante) {
        String sql = "INSERT INTO matches(dartboard_id, locale_id, game_type_id, scoring_rule_id, modalita, "
                + "stato, created_by, local_uuid, clock_offset_ms, data_inizio, data_fine) "
                + "VALUES (?,?,?,?,?,'SINCRONIZZATA',?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, dartboardId);
            ps.setLong(2, localeId);
            ps.setLong(3, gameTypeId);
            ps.setLong(4, scoringRuleId);
            ps.setString(5, modalita.name());
            ps.setLong(6, createdBy);
            ps.setString(7, localUuid);
            ps.setLong(8, clockOffsetMs);
            String now = istante.toString();
            ps.setString(9, now);
            ps.setString(10, now);
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
     * Inserisce la partita nel database.
     *
     * @param entity partita da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    @Override
    public Match insert(Match entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.dartboardId(), entity.localeId(), entity.gameTypeId(),
                    entity.scoringRuleId(), entity.modalita(), entity.createdBy(), entity.localUuid());
            if (entity.localUuid() != null && entity.localUuid().startsWith("tournament:"))
                markSensorTournament(connection, id);

            return new Match(id, entity.dartboardId(), entity.localeId(), entity.gameTypeId(), entity.scoringRuleId(),
                    entity.modalita(), MatchStatus.CREATA, entity.createdBy(), entity.localUuid(), 0, null, null);
        });
    }

    /**
     * Aggiorna i dati della partita nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity partita da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, Match entity) {
        String sql = "UPDATE matches SET stato = ?, clock_offset_ms = ?, data_inizio = ?, data_fine = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.stato().name());
            ps.setLong(2, entity.clockOffsetMs());
            ps.setString(3, entity.dataInizio() == null ? null : entity.dataInizio().toString());
            ps.setString(4, entity.dataFine() == null ? null : entity.dataFine().toString());
            ps.setLong(5, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna i dati della partita nel database.
     *
     * @param entity partita da salvare
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    @Override
    public void update(Match entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina la partita identificato dalla richiesta.
     *
     * @param id identificativo della partita
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM matches WHERE id = ?", id);
            return null;
        });
    }

    /**
     * Associa i valori ai parametri dell'istruzione JDBC.
     *
     * @param ps istruzione JDBC parametrizzata
     * @param params valori da associare ai parametri SQL
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.sql.PreparedStatement
     *  
     */
    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Raccoglie i dati di filtro.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param join clausola di collegamento fra le tabelle della query
     * @param where condizioni di selezione della query SQL
     * @param params valori da associare ai parametri SQL
     * @see java.util.List
     *  
     */
    private record Filtro(String join, String where, List<Object> params) {
    }

    /**
     * Costruisce filtro per la partita.
     *
     * @param playerId identificativo del giocatore
     * @param localeId identificativo del locale
     * @param stato stato da impostare o verificare
     * @param dal limite temporale iniziale
     * @param al limite temporale finale
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dao.MatchDao.Filtro} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dao.MatchDao.Filtro
     *  
     */
    private Filtro costruisciFiltro(Long playerId, Long localeId, MatchStatus stato, Instant dal, Instant al) {
        List<String> condizioni = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String join = "";
        if (playerId != null) {
            join = " JOIN match_participants mp ON mp.match_id = m.id";
            condizioni.add("(mp.player_id = ? OR mp.team_id IN (SELECT team_id FROM team_members WHERE player_id = ?))");
            params.add(playerId);
            params.add(playerId);
        }
        if (localeId != null) {
            condizioni.add("m.locale_id = ?");
            params.add(localeId);
        }
        if (stato != null) {
            condizioni.add("m.stato = ?");
            params.add(stato.name());
        }
        if (dal != null) {
            condizioni.add("m.data_inizio >= ?");
            params.add(dal.toString());
        }
        if (al != null) {
            condizioni.add("m.data_inizio <= ?");
            params.add(al.toString());
        }
        String where = condizioni.isEmpty() ? "" : " WHERE " + String.join(" AND ", condizioni);
        return new Filtro(join, where, params);
    }
}
