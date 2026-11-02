package it.uniupo.pissir.darts.common.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Idempotenza dei consumi MQTT: con cleanSession=false un evento può essere riconsegnato dopo un riavvio del broker o del servizio. 
 */
public final class ProcessedEventDao {

    /**
     * Verifica se l'evento o la partita sono già stati elaborati.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param eventId identificativo dell'evento
     * @return {@code true} se l'evento o la partita sono già stati elaborati; {@code false} altrimenti
     * @see java.sql.Connection
     *  
     */
    public boolean giaElaborato(Connection connection, String eventId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM processed_events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Registra l'evento o la partita fra gli elementi elaborati.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param eventId identificativo dell'evento
     * @see java.sql.Connection
     *  
     */
    public void segnaElaborato(Connection connection, String eventId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO processed_events(event_id, processed_at) VALUES (?, ?)")) {
            ps.setString(1, eventId);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }
}
