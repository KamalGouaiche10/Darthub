package it.uniupo.pissir.darts.match.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.match.model.Throw;
import it.uniupo.pissir.darts.match.model.ThrowOrigin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella throws.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class ThrowDao extends JdbcDao<Throw, Long> {

    /**
     * Inizializza {@code ThrowDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public ThrowDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<Throw> rowMapper() {
        return rs -> {
            String settore = rs.getString("settore");
            String moltiplicatore = rs.getString("moltiplicatore");
            long teamIdValore = rs.getLong("team_id");
            Long teamId = rs.wasNull() ? null : teamIdValore;
            return new Throw(
                    rs.getLong("id"),
                    rs.getLong("match_id"),
                    rs.getLong("participant_id"),
                    rs.getString("tipo"),
                    settore == null ? null : DartSector.valueOf(settore),
                    moltiplicatore == null ? null : Multiplier.valueOf(moltiplicatore),
                    rs.getString("payload_json"),
                    teamId,
                    rs.getInt("punti"),
                    rs.getInt("numero_turno"),
                    Esito.valueOf(rs.getString("esito")),
                    rs.getInt("annullato") == 1,
                    Instant.parse(rs.getString("timestamp_sensore")),
                    Instant.parse(rs.getString("timestamp_ricezione")),
                    ThrowOrigin.valueOf(rs.getString("origine")),
                    rs.getLong("dartboard_id"));
        };
    }

    /**
     * Cerca il tiro per identificativo.
     *
     * @param id identificativo del tiro
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<Throw> findById(Long id) {
        return queryOne("SELECT * FROM throws WHERE id = ?", id);
    }

    /**
     * Cerca tiri per partita.
     *
     * @param matchId identificativo della partita
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Throw> findByMatch(long matchId, Page page) {
        return queryList("SELECT * FROM throws WHERE match_id = ? ORDER BY timestamp_sensore LIMIT ? OFFSET ?",
                matchId, page.limit(), page.offset());
    }

    /**
     * Conta tiri secondo per partita.
     *
     * @param matchId identificativo della partita
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public long countByMatch(long matchId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM throws WHERE match_id = ?")) {
                ps.setLong(1, matchId);
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
     * Cerca tiri rilevanti per partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Throw> findRilevantiByMatch(Connection connection, long matchId) {
        String sql = "SELECT * FROM throws WHERE match_id = ? AND esito != 'IGNORATO' ORDER BY timestamp_sensore, id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Throw> risultato = new java.util.ArrayList<>();
                RowMapper<Throw> mapper = rowMapper();
                while (rs.next()) {
                    risultato.add(mapper.map(rs));
                }
                return risultato;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Inserisce il tiro nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @param tipo tipo della risorsa o dell'evento
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param payloadJson contenuto del messaggio in JSON
     * @param teamId identificativo della squadra
     * @param punti punti assegnati al tiro
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param timestampSensore istante registrato dal sensore
     * @param timestampRicezione istante di ricezione dell'evento
     * @param origine origine dei dati della partita o del tiro
     * @param dartboardId identificativo del bersaglio
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long matchId, long participantId, String tipo, DartSector settore,
                        Multiplier moltiplicatore, String payloadJson, Long teamId, int punti, int numeroTurno,
                        Esito esito, Instant timestampSensore, Instant timestampRicezione, ThrowOrigin origine,
                        long dartboardId) {
        String sql = "INSERT INTO throws(match_id, participant_id, tipo, settore, moltiplicatore, payload_json, "
                + "team_id, punti, numero_turno, esito, annullato, timestamp_sensore, timestamp_ricezione, origine, "
                + "dartboard_id) VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, matchId, participantId, tipo, settore, moltiplicatore, payloadJson, teamId, punti,
                    numeroTurno, esito, timestampSensore, timestampRicezione, origine, dartboardId);
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
     * Inserisce l'elemento senza ripetere una registrazione già presente.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @param tipo tipo della risorsa o dell'evento
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param payloadJson contenuto del messaggio in JSON
     * @param teamId identificativo della squadra
     * @param punti punti assegnati al tiro
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param timestampSensore istante registrato dal sensore
     * @param timestampRicezione istante di ricezione dell'evento
     * @param origine origine dei dati della partita o del tiro
     * @param dartboardId identificativo del bersaglio
     * @return l'esito della condizione verificata
     * @see java.sql.Connection
     *  
     */
    public boolean insertIgnorandoDuplicati(Connection connection, long matchId, long participantId, String tipo,
                                             DartSector settore, Multiplier moltiplicatore, String payloadJson,
                                             Long teamId, int punti, int numeroTurno, Esito esito,
                                             Instant timestampSensore, Instant timestampRicezione,
                                             ThrowOrigin origine, long dartboardId) {
        String sql = "INSERT OR IGNORE INTO throws(match_id, participant_id, tipo, settore, moltiplicatore, "
                + "payload_json, team_id, punti, numero_turno, esito, annullato, timestamp_sensore, "
                + "timestamp_ricezione, origine, dartboard_id) VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindInsert(ps, matchId, participantId, tipo, settore, moltiplicatore, payloadJson, teamId, punti,
                    numeroTurno, esito, timestampSensore, timestampRicezione, origine, dartboardId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Associa all'inserimento i campi dell'entità.
     *
     * @param ps istruzione JDBC parametrizzata
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @param tipo tipo della risorsa o dell'evento
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param payloadJson contenuto del messaggio in JSON
     * @param teamId identificativo della squadra
     * @param punti punti assegnati al tiro
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param timestampSensore istante registrato dal sensore
     * @param timestampRicezione istante di ricezione dell'evento
     * @param origine origine dei dati della partita o del tiro
     * @param dartboardId identificativo del bersaglio
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.sql.PreparedStatement
     *  
     */
    private void bindInsert(PreparedStatement ps, long matchId, long participantId, String tipo, DartSector settore,
                             Multiplier moltiplicatore, String payloadJson, Long teamId, int punti, int numeroTurno,
                             Esito esito, Instant timestampSensore, Instant timestampRicezione, ThrowOrigin origine,
                             long dartboardId) throws SQLException {
        ps.setLong(1, matchId);
        ps.setLong(2, participantId);
        ps.setString(3, tipo);
        ps.setString(4, settore == null ? null : settore.name());
        ps.setString(5, moltiplicatore == null ? null : moltiplicatore.name());
        ps.setString(6, payloadJson);
        if (teamId != null) {
            ps.setLong(7, teamId);
        } else {
            ps.setNull(7, java.sql.Types.INTEGER);
        }
        ps.setInt(8, punti);
        ps.setInt(9, numeroTurno);
        ps.setString(10, esito.name());
        ps.setString(11, timestampSensore.toString());
        ps.setString(12, timestampRicezione.toString());
        ps.setString(13, origine.name());
        ps.setLong(14, dartboardId);
    }

    /**
     * Ripristina il punteggio precedente all'inizio del turno.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param participantId identificativo del partecipante
     * @param numeroTurno numero del turno
     * @see java.sql.Connection
     *  
     */
    public void annullaTurno(Connection connection, long participantId, int numeroTurno) {
        executeUpdate(connection,
                "UPDATE throws SET annullato = 1 WHERE participant_id = ? AND numero_turno = ? AND annullato = 0",
                participantId, numeroTurno);
    }

    /**
     * Cerca tiri tutti per partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Throw> findAllByMatch(Connection connection, long matchId) {
        String sql = "SELECT * FROM throws WHERE match_id = ? ORDER BY timestamp_sensore, id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Throw> risultato = new java.util.ArrayList<>();
                RowMapper<Throw> mapper = rowMapper();
                while (rs.next()) {
                    risultato.add(mapper.map(rs));
                }
                return risultato;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna esito ricalcolato per il tiro.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param throwId identificativo del tiro
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param annullato indicatore di annullamento del tiro
     * @see java.sql.Connection
     *  
     */
    public void aggiornaEsitoRicalcolato(Connection connection, long throwId, int numeroTurno, Esito esito, boolean annullato) {
        executeUpdate(connection, "UPDATE throws SET numero_turno = ?, esito = ?, annullato = ? WHERE id = ?",
                numeroTurno, esito.name(), annullato ? 1 : 0, throwId);
    }

    /**
     * Legge una pagina di tiri.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<Throw> findAll(Page page) {
        return queryList("SELECT * FROM throws ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Conta tiri presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM throws")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il tiro nel database.
     *
     * @param entity tiro da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Throw} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.Throw
     *  
     */
    @Override
    public Throw insert(Throw entity) {
        long id = connectionProvider.withTransaction(connection -> insert(connection, entity.matchId(),
                entity.participantId(), entity.tipo(), entity.settore(), entity.moltiplicatore(),
                entity.payloadJson(), entity.teamId(), entity.punti(), entity.numeroTurno(), entity.esito(),
                entity.timestampSensore(), entity.timestampRicezione(), entity.origine(), entity.dartboardId()));
        return findById(id).orElseThrow();
    }

    /**
     * Aggiorna i dati del tiro nel database.
     *
     * @param entity tiro da salvare
     * @see it.uniupo.pissir.darts.match.model.Throw
     *  
     */
    @Override
    public void update(Throw entity) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "UPDATE throws SET annullato = ? WHERE id = ?",
                    entity.annullato() ? 1 : 0, entity.id());
            return null;
        });
    }

    /**
     * Elimina il tiro identificato dalla richiesta.
     *
     * @param id identificativo del tiro
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM throws WHERE id = ?", id);
            return null;
        });
    }
}
