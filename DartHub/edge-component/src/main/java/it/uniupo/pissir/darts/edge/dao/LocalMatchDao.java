package it.uniupo.pissir.darts.edge.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchOrigin;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.LocalParticipant;
import it.uniupo.pissir.darts.edge.model.RosterEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO di local_matches e local_participants: tabelle sempre usate insieme (un partecipante esiste solo nel contesto della sua partita), un solo DAO per entrambe, come nel tree del paragrafo.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class LocalMatchDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code LocalMatchDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public LocalMatchDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca partite locali per server identificativo.
     *
     * @param serverId identificativo assegnato dal server centrale
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<LocalMatch> findByServerId(long serverId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM local_matches WHERE server_id = ?")) {
                ps.setLong(1, serverId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapMatch(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca partite locali in corso.
     *
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<LocalMatch> findInCorso() {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM local_matches WHERE stato = 'IN_CORSO' ORDER BY data_inizio DESC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapMatch(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca partite locali con tiri da sincronizzare.
     *
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<LocalMatch> findConTiriDaSincronizzare() {
        String sql = "SELECT DISTINCT m.* FROM local_matches m JOIN buffered_throws t ON t.local_uuid = m.local_uuid "
                + "WHERE m.origine = 'SERVER' AND t.sincronizzato = 0";
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                List<LocalMatch> risultato = new ArrayList<>();
                while (rs.next()) {
                    risultato.add(mapMatch(rs));
                }
                return risultato;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Cerca partite locali da importare.
     *
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<LocalMatch> findDaImportare() {
        String sql = "SELECT * FROM local_matches WHERE origine = 'LOCALE' AND importata = 0 AND stato = 'COMPLETATA'";
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                List<LocalMatch> risultato = new ArrayList<>();
                while (rs.next()) {
                    risultato.add(mapMatch(rs));
                }
                return risultato;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce la partita locale nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param match partita locale da elaborare
     * @see java.sql.Connection
     *  
     */
    public void insert(Connection connection, LocalMatch match) {
        String sql = "INSERT INTO local_matches(local_uuid, server_id, dartboard_id, scoring_rule, origine, stato, "
                + "importata, data_inizio, data_fine) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, match.localUuid());
            ps.setObject(2, match.serverId());
            ps.setLong(3, match.dartboardId());
            ps.setString(4, match.scoringRuleJson());
            ps.setString(5, match.origine().name());
            ps.setString(6, match.stato().name());
            ps.setInt(7, match.importata() ? 1 : 0);
            ps.setString(8, match.dataInizio().toString());
            ps.setString(9, match.dataFine() == null ? null : match.dataFine().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Salva la partita locale insieme alla regola e ai partecipanti.
     *
     * @param match partita locale da elaborare
     * @param roster formazione dei partecipanti della partita
     * @param puntiIniziali punteggio di partenza
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    public void insertMatchWithRoster(LocalMatch match, List<RosterEntry> roster, int puntiIniziali) {
        connectionProvider.withTransaction(connection -> {
            insert(connection, match);
            for (RosterEntry voce : roster) {
                insertParticipant(connection, match.localUuid(), voce.participantId(), null, voce.nome(),
                        voce.ordineTurno(), puntiIniziali);
            }
            return null;
        });
    }

    /**
     * Aggiorna i dati della partita locale nel database.
     *
     * @param match partita locale da elaborare
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    public void update(LocalMatch match) {
        connectionProvider.withTransaction(connection -> {
            update(connection, match);
            return null;
        });
    }

    /**
     * Aggiorna i dati della partita locale nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param match partita locale da elaborare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, LocalMatch match) {
        String sql = "UPDATE local_matches SET server_id = ?, stato = ?, importata = ?, data_fine = ? WHERE local_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, match.serverId());
            ps.setString(2, match.stato().name());
            ps.setInt(3, match.importata() ? 1 : 0);
            ps.setString(4, match.dataFine() == null ? null : match.dataFine().toString());
            ps.setString(5, match.localUuid());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte la riga JDBC in una partita.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.model.LocalMatch} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    private LocalMatch mapMatch(ResultSet rs) throws SQLException {
        return new LocalMatch(
                rs.getString("local_uuid"),
                rs.getObject("server_id") != null ? rs.getLong("server_id") : null,
                rs.getLong("dartboard_id"),
                rs.getString("scoring_rule"),
                LocalMatchOrigin.valueOf(rs.getString("origine")),
                LocalMatchStatus.valueOf(rs.getString("stato")),
                rs.getInt("importata") == 1,
                Instant.parse(rs.getString("data_inizio")),
                rs.getString("data_fine") == null ? null : Instant.parse(rs.getString("data_fine")));
    }

    /**
     * Cerca partite locali partecipanti per partita.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<LocalParticipant> findParticipantsByMatch(String localUuid) {
        return connectionProvider.withConnection(connection -> findParticipantsByMatch(connection, localUuid));
    }

    /**
     * Cerca partite locali partecipanti per partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localUuid identificativo univoco della partita creata offline
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<LocalParticipant> findParticipantsByMatch(Connection connection, String localUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM local_participants WHERE local_uuid = ? ORDER BY ordine_turno")) {
            ps.setString(1, localUuid);
            try (ResultSet rs = ps.executeQuery()) {
                List<LocalParticipant> risultato = new ArrayList<>();
                while (rs.next()) {
                    risultato.add(mapParticipant(rs));
                }
                return risultato;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Cerca partite locali partecipante per identificativo.
     *
     * @param id identificativo della partita locale
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<LocalParticipant> findParticipantById(long id) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM local_participants WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapParticipant(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il partecipante nella partita locale.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localUuid identificativo univoco della partita creata offline
     * @param serverParticipantId identificativo del partecipante sul server
     * @param playerId identificativo del giocatore
     * @param nomeVisualizzato nome da mostrare nell'interfaccia
     * @param ordineTurno posizione del partecipante nell'ordine di gioco
     * @param punteggioCorrente punteggio attuale del partecipante
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insertParticipant(Connection connection, String localUuid, Long serverParticipantId, Long playerId,
                                   String nomeVisualizzato, int ordineTurno, int punteggioCorrente) {
        String sql = "INSERT INTO local_participants(local_uuid, server_participant_id, player_id, "
                + "nome_visualizzato, ordine_turno, punteggio_corrente) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, localUuid);
            ps.setObject(2, serverParticipantId);
            ps.setObject(3, playerId);
            ps.setString(4, nomeVisualizzato);
            ps.setInt(5, ordineTurno);
            ps.setInt(6, punteggioCorrente);
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
     * Aggiorna il punteggio del partecipante locale.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param participantId identificativo del partecipante
     * @param punteggioCorrente punteggio attuale del partecipante
     * @see java.sql.Connection
     *  
     */
    public void updateParticipantScore(Connection connection, long participantId, int punteggioCorrente) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE local_participants SET punteggio_corrente = ? WHERE id = ?")) {
            ps.setInt(1, punteggioCorrente);
            ps.setLong(2, participantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Converte la riga JDBC in un partecipante.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.model.LocalParticipant} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.edge.model.LocalParticipant
     *  
     */
    private LocalParticipant mapParticipant(ResultSet rs) throws SQLException {
        return new LocalParticipant(
                rs.getLong("id"),
                rs.getString("local_uuid"),
                rs.getObject("server_participant_id") != null ? rs.getLong("server_participant_id") : null,
                rs.getObject("player_id") != null ? rs.getLong("player_id") : null,
                rs.getString("nome_visualizzato"),
                rs.getInt("ordine_turno"),
                rs.getInt("punteggio_corrente"));
    }
}
