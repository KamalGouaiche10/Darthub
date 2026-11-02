package it.uniupo.pissir.darts.locale.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO;
import it.uniupo.pissir.darts.locale.dto.EdgeDeviceStatusDTO;
import it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceRequestDTO;
import it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceResponseDTO;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.EdgeConfig;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;
import it.uniupo.pissir.darts.locale.mqtt.EdgeConfigPublisher;

import java.util.List;
import java.time.Instant;

/**
 * Registrazione, configurazione, revoca dei dispositivi edge.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class EdgeDeviceService {

    private static final int DEFAULT_HEARTBEAT_S = 30;
    private static final int DEFAULT_OFFLINE_THRESHOLD_S = 90;
    private static final int DEFAULT_QOS = 2;

    private final ConnectionProvider connectionProvider;
    private final EdgeDeviceDao edgeDeviceDao;
    private final DartboardService dartboardService;
    private final EdgeConfigPublisher edgeConfigPublisher;
    private final ServiceHttpClient httpClient;
    private final String userServiceBaseUrl;

    /**
     * Inizializza {@code EdgeDeviceService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param edgeDeviceDao componente {@link it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao} utilizzato dall'operazione
     * @param dartboardService componente {@link it.uniupo.pissir.darts.locale.service.DartboardService} utilizzato dall'operazione
     * @param edgeConfigPublisher componente {@link it.uniupo.pissir.darts.locale.mqtt.EdgeConfigPublisher} utilizzato dall'operazione
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param userServiceBaseUrl indirizzo di base dell'utente servizio
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public EdgeDeviceService(ConnectionProvider connectionProvider, EdgeDeviceDao edgeDeviceDao,
                              DartboardService dartboardService, EdgeConfigPublisher edgeConfigPublisher,
                              ServiceHttpClient httpClient, String userServiceBaseUrl) {
        this.connectionProvider = connectionProvider;
        this.edgeDeviceDao = edgeDeviceDao;
        this.dartboardService = dartboardService;
        this.edgeConfigPublisher = edgeConfigPublisher;
        this.httpClient = httpClient;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    /**
     * Verifica il requisito edge device prima di eseguire l'operazione.
     *
     * @param id identificativo del dispositivo edge
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.EdgeDevice} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Dispositivo edge non trovato»
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    public EdgeDevice requireEdgeDevice(long id) {
        return edgeDeviceDao.findById(id)
                .orElseThrow(() -> new NotFoundException("DISPOSITIVO_NON_TROVATO", "Dispositivo edge non trovato"));
    }

    /**
     * Recupera l'identificativo del proprietario della risorsa.
     *
     * @param edgeDeviceId identificativo del dispositivo edge
     * @return il valore numerico calcolato o letto
     *  
     */
    public long ownerOf(long edgeDeviceId) {
        return dartboardService.ownerOf(requireEdgeDevice(edgeDeviceId).dartboardId());
    }

    /**
     * Registra il dispositivo edge applicando i controlli previsti.
     *
     * @param request richiesta da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceResponseDTO} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: dartboardId»
     * @see it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceResponseDTO
     *  
     */
    public RegisterEdgeDeviceResponseDTO register(RegisterEdgeDeviceRequestDTO request) {
        if (request.dartboardId() == null) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: dartboardId");
        }
        if (request.serialNumber() == null || request.serialNumber().isBlank()) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: serialNumber");
        }
        Dartboard dartboard = dartboardService.requireDartboard(request.dartboardId());

        int heartbeat = request.intervalloHeartbeatS() != null ? request.intervalloHeartbeatS() : DEFAULT_HEARTBEAT_S;
        int soglia = request.sogliaOfflineS() != null ? request.sogliaOfflineS() : DEFAULT_OFFLINE_THRESHOLD_S;
        int qos = request.qos() != null ? request.qos() : DEFAULT_QOS;
        String urlBroker = request.urlBrokerLocale();

        return connectionProvider.withTransaction(connection -> {
            long deviceId = edgeDeviceDao.insert(connection, dartboard.id(), dartboard.localeId(),
                    request.serialNumber(), heartbeat, soglia, qos, urlBroker);

            DeviceTokenResponse tokenResponse;
            try {
                DeviceTokenRequest tokenRequest = new DeviceTokenRequest(dartboard.localeId(), dartboard.id(),
                        request.serialNumber());
                tokenResponse = httpClient.post(userServiceBaseUrl + "/users/device-token", tokenRequest, null,
                        DeviceTokenResponse.class);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Registrazione del dispositivo edge fallita: user-service non ha emesso il token", e);
            }

            edgeDeviceDao.update(connection, new EdgeDevice(deviceId, dartboard.id(), dartboard.localeId(),
                    request.serialNumber(), heartbeat, soglia, qos, urlBroker, null, 0, EdgeDeviceStatus.OFFLINE,
                    tokenResponse.deviceUserId()));

            return new RegisterEdgeDeviceResponseDTO(deviceId, tokenResponse.token(),
                    new EdgeConfig(heartbeat, soglia, qos, urlBroker));
        });
    }

    /**
     * Revoca il token o le credenziali indicati.
     *
     * @param id identificativo del dispositivo edge
     *  
     */
    public void revoke(long id) {
        EdgeDevice device = requireEdgeDevice(id);
        if (device.deviceUserId() != null) {
            try {
                httpClient.delete(userServiceBaseUrl + "/users/device-token/" + device.deviceUserId(), null);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Revoca del token del dispositivo edge fallita", e);
            }
        }
        edgeDeviceDao.delete(id);
    }

    /**
     * Cerca dispositivi edge tutti.
     *
     * @param localeIdFilter filtro per identificativo del locale
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<EdgeDeviceDTO> findAll(Long localeIdFilter, Page page) {
        List<EdgeDevice> list = localeIdFilter != null
                ? edgeDeviceDao.findByLocale(localeIdFilter, page)
                : edgeDeviceDao.findAll(page);
        long total = localeIdFilter != null ? edgeDeviceDao.countByLocale(localeIdFilter) : edgeDeviceDao.count();
        return PagedResult.of(list.stream().map(this::toDto).toList(), page, total);
    }

    /**
     * Cerca il dispositivo edge usando i dati della richiesta.
     *
     * @param id identificativo del dispositivo edge
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO
     *  
     */
    public EdgeDeviceDTO findByIdDto(long id) {
        return toDto(requireEdgeDevice(id));
    }

    /**
     * Restituisce lo stato del dispositivo edge.
     *
     * @param id identificativo del dispositivo edge
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.EdgeDeviceStatusDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.EdgeDeviceStatusDTO
     *  
     */
    public EdgeDeviceStatusDTO status(long id) {
        EdgeDevice device = requireEdgeDevice(id);
        return new EdgeDeviceStatusDTO(device.id(), device.stato().name(),
                device.ultimoHeartbeat() != null ? device.ultimoHeartbeat().toString() : null, device.tiriInCoda());
    }

    /**
     * Riserva il bersaglio per l'avvio della partita.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @throws ValidationException se viene rilevato l'errore «Locale o gioco non corrispondono al bersaglio»
     * @throws ConflictException se viene rilevato l'errore «Il dispositivo edge non è online»
     *  
     */
    public void reserveDartboard(long dartboardId, long localeId, long gameTypeId) {
        Dartboard dartboard = dartboardService.requireDartboard(dartboardId);
        if (dartboard.localeId() != localeId || dartboard.gameTypeId() != gameTypeId) {
            throw new ValidationException("BERSAGLIO_INCOERENTE", "Locale o gioco non corrispondono al bersaglio");
        }
        EdgeDevice device = edgeDeviceDao.findByDartboardId(dartboardId)
                .orElseThrow(() -> new ConflictException("EDGE_OFFLINE", "Nessun edge registrato per il bersaglio"));
        if (device.stato() != EdgeDeviceStatus.ONLINE || device.ultimoHeartbeat() == null
                || device.ultimoHeartbeat().isBefore(Instant.now().minusSeconds(device.sogliaOfflineS()))) {
            throw new ConflictException("EDGE_OFFLINE", "Il dispositivo edge non è online");
        }
        dartboardService.reserve(dartboardId);
    }

    /**
     * Aggiorna i parametri di configurazione del dispositivo.
     *
     * @param id identificativo del dispositivo edge
     * @param config configurazione del componente
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Heartbeat, soglia offline o QoS non validi»
     * @see it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO
     *  
     */
    public EdgeDeviceDTO updateConfig(long id, EdgeConfig config) {
        if (config.intervalloHeartbeatS() < 1 || config.sogliaOfflineS() <= config.intervalloHeartbeatS()
                || config.qos() < 0 || config.qos() > 2) {
            throw new ValidationException("CONFIG_EDGE_NON_VALIDA", "Heartbeat, soglia offline o QoS non validi");
        }
        if (config.urlBrokerLocale() != null && !config.urlBrokerLocale().isBlank()) {
            try {
                var uri = java.net.URI.create(config.urlBrokerLocale());
                if (!("tcp".equals(uri.getScheme()) || "ssl".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new ValidationException("URL_BROKER_NON_VALIDO", "Usare un URL tcp:// o ssl:// senza credenziali"); }
        }
        EdgeDevice current = requireEdgeDevice(id);
        EdgeDevice updated = new EdgeDevice(current.id(), current.dartboardId(), current.localeId(),
                current.serialNumber(), config.intervalloHeartbeatS(), config.sogliaOfflineS(), config.qos(),
                config.urlBrokerLocale(), current.ultimoHeartbeat(), current.tiriInCoda(), current.stato(),
                current.deviceUserId());
        edgeDeviceDao.update(updated);
        edgeConfigPublisher.publish(current.localeId(), current.dartboardId(), config);
        return toDto(updated);
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param device dispositivo edge da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.EdgeDeviceDTO
     *  
     */
    private EdgeDeviceDTO toDto(EdgeDevice device) {
        return new EdgeDeviceDTO(device.id(), device.dartboardId(), device.localeId(), device.serialNumber(),
                device.intervalloHeartbeatS(), device.sogliaOfflineS(), device.qos(), device.urlBrokerLocale(),
                device.ultimoHeartbeat() != null ? device.ultimoHeartbeat().toString() : null, device.tiriInCoda(),
                device.stato().name(), device.deviceUserId());
    }

    /**
     * Contratto interno con POST /users/device-token di user-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param serialNumber numero seriale del dispositivo
     *  
     */
    private record DeviceTokenRequest(long localeId, long dartboardId, String serialNumber) {
    }

    /**
     * Contratto interno con la risposta di user-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param deviceUserId identificativo dell'account tecnico del dispositivo
     * @param token token da utilizzare
     *  
     */
    private record DeviceTokenResponse(long deviceUserId, String token) {
    }
}
