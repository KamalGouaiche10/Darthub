package it.uniupo.pissir.darts.edge.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.edge.model.BufferedThrow;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO di buffered_throws.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class BufferedThrowDao {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code BufferedThrowDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public BufferedThrowDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Cerca tiri da sincronizzare per partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localUuid identificativo univoco della partita creata offline
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<BufferedThrow> findByMatch(Connection connection, String localUuid) {
        String sql = "SELECT * FROM buffered_throws WHERE local_uuid = ? ORDER BY timestamp_sensore, id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, localUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return leggiTutti(rs);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Cerca tiri da sincronizzare per partita.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<BufferedThrow> findByMatch(String localUuid) {
        return connectionProvider.withConnection(connection -> findByMatch(connection, localUuid));
    }

    /**
     * Cerca tiri da sincronizzare pendenti per partita.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<BufferedThrow> findPendentiByMatch(String localUuid) {
        String sql = "SELECT * FROM buffered_throws WHERE local_uuid = ? AND sincronizzato = 0 "
                + "ORDER BY timestamp_sensore, id";
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, localUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    return leggiTutti(rs);
                }
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Conta tiri da sincronizzare secondo pendenti.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public int countPendenti() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM buffered_throws WHERE sincronizzato = 0")) {
                rs.next();
                return rs.getInt(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Inserisce il tiro da sincronizzare nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localUuid identificativo univoco della partita creata offline
     * @param localParticipantId identificativo del partecipante locale
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @param numeroTurno numero del turno
     * @param esito esito del tiro o della partita
     * @param annullato indicatore di annullamento del tiro
     * @param timestampSensore istante registrato dal sensore
     * @param sincronizzato indicatore di sincronizzazione con il server
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, String localUuid, long localParticipantId, DartSector settore,
                        Multiplier moltiplicatore, int punti, int numeroTurno, Esito esito, boolean annullato,
                        Instant timestampSensore, boolean sincronizzato) {
        String sql = "INSERT INTO buffered_throws(local_uuid, local_participant_id, settore, moltiplicatore, punti, "
                + "numero_turno, esito, annullato, timestamp_sensore, sincronizzato) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, localUuid);
            ps.setLong(2, localParticipantId);
            ps.setString(3, settore.name());
            ps.setString(4, moltiplicatore.name());
            ps.setInt(5, punti);
            ps.setInt(6, numeroTurno);
            ps.setString(7, esito.name());
            ps.setInt(8, annullato ? 1 : 0);
            ps.setString(9, timestampSensore.toString());
            ps.setInt(10, sincronizzato ? 1 : 0);
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
     * Segna il tiro come sincronizzato con il sistema centrale.
     *
     * @param id identificativo del tiro da sincronizzare
     *  
     */
    public void markSincronizzato(long id) {
        connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE buffered_throws SET sincronizzato = 1 WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Legge tutti per il tiro da sincronizzare.
     *
     * @param rs riga corrente del risultato JDBC
     * @return gli elementi trovati o prodotti dall'operazione
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.util.List
     *  
     */
    private List<BufferedThrow> leggiTutti(ResultSet rs) throws SQLException {
        List<BufferedThrow> risultato = new ArrayList<>();
        while (rs.next()) {
            risultato.add(new BufferedThrow(
                    rs.getLong("id"),
                    rs.getString("local_uuid"),
                    rs.getLong("local_participant_id"),
                    DartSector.valueOf(rs.getString("settore")),
                    Multiplier.valueOf(rs.getString("moltiplicatore")),
                    rs.getInt("punti"),
                    rs.getInt("numero_turno"),
                    Esito.valueOf(rs.getString("esito")),
                    rs.getInt("annullato") == 1,
                    Instant.parse(rs.getString("timestamp_sensore")),
                    rs.getInt("sincronizzato") == 1));
        }
        return risultato;
    }
}
