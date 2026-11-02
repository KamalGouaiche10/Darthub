package it.uniupo.pissir.darts.locale.dao;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.JdbcDao;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.RowMapper;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DAO della tabella edge_devices.
 *
 * @see it.uniupo.pissir.darts.common.persistence.JdbcDao
 *  
 */
public final class EdgeDeviceDao extends JdbcDao<EdgeDevice, Long> {

    /**
     * Inizializza {@code EdgeDeviceDao} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public EdgeDeviceDao(ConnectionProvider connectionProvider) {
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
    protected RowMapper<EdgeDevice> rowMapper() {
        return rs -> new EdgeDevice(
                rs.getLong("id"),
                rs.getLong("dartboard_id"),
                rs.getLong("locale_id"),
                rs.getString("serial_number"),
                rs.getInt("intervallo_heartbeat_s"),
                rs.getInt("soglia_offline_s"),
                rs.getInt("qos"),
                rs.getString("url_broker_locale"),
                rs.getString("ultimo_heartbeat") != null ? Instant.parse(rs.getString("ultimo_heartbeat")) : null,
                rs.getInt("tiri_in_coda"),
                EdgeDeviceStatus.valueOf(rs.getString("stato")),
                rs.getObject("device_user_id") != null ? rs.getLong("device_user_id") : null);
    }

    /**
     * Cerca il dispositivo edge per identificativo.
     *
     * @param id identificativo del dispositivo edge
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    @Override
    public Optional<EdgeDevice> findById(Long id) {
        return queryOne("SELECT * FROM edge_devices WHERE id = ?", id);
    }

    /**
     * Cerca dispositivi edge per bersaglio identificativo.
     *
     * @param dartboardId identificativo del bersaglio
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<EdgeDevice> findByDartboardId(long dartboardId) {
        return queryOne("SELECT * FROM edge_devices WHERE dartboard_id = ?", dartboardId);
    }

    /**
     * Legge una pagina di dispositivi edge.
     *
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    @Override
    public List<EdgeDevice> findAll(Page page) {
        return queryList("SELECT * FROM edge_devices ORDER BY id LIMIT ? OFFSET ?", page.limit(), page.offset());
    }

    /**
     * Cerca dispositivi edge per locale.
     *
     * @param localeId identificativo del locale
     * @param page parametri di paginazione
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<EdgeDevice> findByLocale(long localeId, Page page) {
        return queryList("SELECT * FROM edge_devices WHERE locale_id = ? ORDER BY id LIMIT ? OFFSET ?",
                localeId, page.limit(), page.offset());
    }

    /**
     * Cerca dispositivi edge online.
     *
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<EdgeDevice> findOnline() {
        return queryList("SELECT * FROM edge_devices WHERE stato = 'ONLINE'");
    }

    /**
     * Conta dispositivi edge presenti nel database.
     *
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    @Override
    public long count() {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM edge_devices")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Conta dispositivi edge secondo per locale.
     *
     * @param localeId identificativo del locale
     * @return il numero di elementi corrispondenti ai criteri
     *  
     */
    public long countByLocale(long localeId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM edge_devices WHERE locale_id = ?")) {
                ps.setLong(1, localeId);
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
     * Aggiorna i dati dell'ultimo segnale di presenza del bersaglio.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param timestamp istante dell'evento
     * @param tiriInCoda numero di tiri in attesa di sincronizzazione
     * @param stato stato da impostare o verificare
     * @see java.sql.Connection
     *  
     */
    public void updateHeartbeatForDartboard(Connection connection, long localeId, long dartboardId,
                                          Instant timestamp, int tiriInCoda,
                                          EdgeDeviceStatus stato) {
        String sql = "UPDATE edge_devices SET ultimo_heartbeat = ?, tiri_in_coda = ?, stato = ? "
                + "WHERE locale_id = ? AND dartboard_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, timestamp.toString());
            ps.setInt(2, tiriInCoda);
            ps.setString(3, stato.name());
            ps.setLong(4, localeId);
            ps.setLong(5, dartboardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Segna offline il dispositivo se non è arrivato un nuovo heartbeat.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param id identificativo del dispositivo edge
     * @param ultimoHeartbeat istante dell'ultimo segnale di presenza
     * @return l'esito della condizione verificata
     * @see java.sql.Connection
     *  
     */
    public boolean markOfflineIfHeartbeatUnchanged(Connection connection, long id, Instant ultimoHeartbeat) {
        return executeUpdate(connection, "UPDATE edge_devices SET stato = 'OFFLINE' "
                + "WHERE id = ? AND stato = 'ONLINE' AND ultimo_heartbeat = ?", id,
                ultimoHeartbeat.toString()) == 1;
    }

    /**
     * Inserisce il dispositivo edge nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param serialNumber numero seriale del dispositivo
     * @param intervalloHeartbeatS intervallo dei segnali di presenza in secondi
     * @param sogliaOfflineS tempo senza heartbeat prima dello stato offline, in secondi
     * @param qos livello MQTT di qualità del servizio
     * @param urlBrokerLocale indirizzo del broker MQTT locale
     * @return il valore numerico calcolato o letto
     * @see java.sql.Connection
     *  
     */
    public long insert(Connection connection, long dartboardId, long localeId, String serialNumber,
                        int intervalloHeartbeatS, int sogliaOfflineS, int qos, String urlBrokerLocale) {
        String sql = "INSERT INTO edge_devices(dartboard_id, locale_id, serial_number, intervallo_heartbeat_s, "
                + "soglia_offline_s, qos, url_broker_locale, tiri_in_coda, stato) VALUES (?,?,?,?,?,?,?,0,'OFFLINE')";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, dartboardId);
            ps.setLong(2, localeId);
            ps.setString(3, serialNumber);
            ps.setInt(4, intervalloHeartbeatS);
            ps.setInt(5, sogliaOfflineS);
            ps.setInt(6, qos);
            ps.setString(7, urlBrokerLocale);
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
     * Inserisce il dispositivo edge nel database.
     *
     * @param entity dispositivo edge da salvare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.EdgeDevice} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    @Override
    public EdgeDevice insert(EdgeDevice entity) {
        return connectionProvider.withTransaction(connection -> {
            long id = insert(connection, entity.dartboardId(), entity.localeId(), entity.serialNumber(),
                    entity.intervalloHeartbeatS(), entity.sogliaOfflineS(), entity.qos(), entity.urlBrokerLocale());
            return new EdgeDevice(id, entity.dartboardId(), entity.localeId(), entity.serialNumber(),
                    entity.intervalloHeartbeatS(), entity.sogliaOfflineS(), entity.qos(), entity.urlBrokerLocale(),
                    null, 0, EdgeDeviceStatus.OFFLINE, entity.deviceUserId());
        });
    }

    /**
     * Aggiorna i dati del dispositivo edge nel database.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param entity dispositivo edge da salvare
     * @see java.sql.Connection
     *  
     */
    public void update(Connection connection, EdgeDevice entity) {
        String sql = "UPDATE edge_devices SET serial_number = ?, intervallo_heartbeat_s = ?, soglia_offline_s = ?, "
                + "qos = ?, url_broker_locale = ?, ultimo_heartbeat = ?, tiri_in_coda = ?, stato = ?, "
                + "device_user_id = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.serialNumber());
            ps.setInt(2, entity.intervalloHeartbeatS());
            ps.setInt(3, entity.sogliaOfflineS());
            ps.setInt(4, entity.qos());
            ps.setString(5, entity.urlBrokerLocale());
            ps.setString(6, entity.ultimoHeartbeat() != null ? entity.ultimoHeartbeat().toString() : null);
            ps.setInt(7, entity.tiriInCoda());
            ps.setString(8, entity.stato().name());
            if (entity.deviceUserId() != null) {
                ps.setLong(9, entity.deviceUserId());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.setLong(10, entity.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(e);
        }
    }

    /**
     * Aggiorna i dati del dispositivo edge nel database.
     *
     * @param entity dispositivo edge da salvare
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    @Override
    public void update(EdgeDevice entity) {
        connectionProvider.withTransaction(connection -> {
            update(connection, entity);
            return null;
        });
    }

    /**
     * Elimina il dispositivo edge identificato dalla richiesta.
     *
     * @param id identificativo del dispositivo edge
     *  
     */
    @Override
    public void delete(Long id) {
        connectionProvider.withTransaction(connection -> {
            executeUpdate(connection, "DELETE FROM edge_devices WHERE id = ?", id);
            return null;
        });
    }
}
