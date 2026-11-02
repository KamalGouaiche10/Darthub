package it.uniupo.pissir.darts.locale.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceRequestDTO;
import it.uniupo.pissir.darts.locale.model.EdgeConfig;
import it.uniupo.pissir.darts.locale.model.Role;
import it.uniupo.pissir.darts.locale.security.LocaleAccessRules;
import it.uniupo.pissir.darts.locale.service.DartboardService;
import it.uniupo.pissir.darts.locale.service.EdgeDeviceService;
import it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * POST /edge-devices/register, GET /edge-devices[/{id[/stato]]}, PUT /edge-devices/{id/config}, DELETE /edge-devices/{id}.
 *
 * @see it.uniupo.pissir.darts.locale.service.EdgeDeviceService
 *  
 */
public final class EdgeDeviceController {

    private final EdgeDeviceService edgeDeviceService;
    private final DartboardService dartboardService;
    private final LocaleService localeService;
    private final JwtTokenService jwtTokenService;
    private final ActiveMatchQueryService activeMatchQueryService;

    /**
     * Inizializza {@code EdgeDeviceController} con i dati e le dipendenze indicati.
     *
     * @param edgeDeviceService componente {@link it.uniupo.pissir.darts.locale.service.EdgeDeviceService} utilizzato dall'operazione
     * @param dartboardService componente {@link it.uniupo.pissir.darts.locale.service.DartboardService} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.locale.service.EdgeDeviceService
     *  
     */
    public EdgeDeviceController(EdgeDeviceService edgeDeviceService, DartboardService dartboardService,
                                 LocaleService localeService, JwtTokenService jwtTokenService) {
        this(edgeDeviceService, dartboardService, localeService, jwtTokenService, null);
    }

    /**
     * Inizializza {@code EdgeDeviceController} con i dati e le dipendenze indicati.
     *
     * @param edgeDeviceService componente {@link it.uniupo.pissir.darts.locale.service.EdgeDeviceService} utilizzato dall'operazione
     * @param dartboardService componente {@link it.uniupo.pissir.darts.locale.service.DartboardService} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param activeMatchQueryService componente {@link it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.service.EdgeDeviceService
     *  
     */
    public EdgeDeviceController(EdgeDeviceService edgeDeviceService, DartboardService dartboardService,
                                 LocaleService localeService, JwtTokenService jwtTokenService,
                                 ActiveMatchQueryService activeMatchQueryService) {
        this.edgeDeviceService = edgeDeviceService;
        this.dartboardService = dartboardService;
        this.localeService = localeService;
        this.jwtTokenService = jwtTokenService;
        this.activeMatchQueryService = activeMatchQueryService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/edge-devices/register", this::registerDevice);
        Spark.post("/edge-devices/bersagli/:id/prenota", this::reserveDartboard);
        Spark.get("/edge-devices", this::findAll);
        Spark.get("/edge-devices/:id", this::findById);
        Spark.get("/edge-devices/:id/stato", this::status);
        Spark.put("/edge-devices/:id/config", this::updateConfig);
        Spark.delete("/edge-devices/:id", this::revoke);
    }

    /**
     * Riserva il bersaglio per l'avvio della partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «localeId e gameTypeId sono obbligatori»  
     *  
     */
    private Object reserveDartboard(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, "SERVICE");
        ReserveDartboardRequest body = JsonSupport.fromJson(req.body(), ReserveDartboardRequest.class);
        if (body == null || body.localeId() == null || body.gameTypeId() == null) {
            throw new ValidationException("localeId e gameTypeId sono obbligatori");
        }
        edgeDeviceService.reserveDartboard(ControllerSupport.pathLong(req, "id"), body.localeId(), body.gameTypeId());
        res.status(204);
        return "";
    }

    /**
     * Raccoglie i dati della richiesta relativa a reserve bersaglio.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     *  
     */
    private record ReserveDartboardRequest(Long localeId, Long gameTypeId) {
    }

    /**
     * Registra l'account e le credenziali del dispositivo edge.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: dartboardId»  
     *  
     */
    private Object registerDevice(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RegisterEdgeDeviceRequestDTO body = JsonSupport.fromJson(req.body(), RegisterEdgeDeviceRequestDTO.class);
        if (body.dartboardId() == null) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: dartboardId");
        }
        if (!user.hasAnyRole("SERVICE")) {
            LocaleAccessRules.requireOwnerOrPlatformAdmin(user, dartboardService.ownerOf(body.dartboardId()));
        }
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(edgeDeviceService.register(body));
    }

    /**
     * Cerca dispositivi edge tutti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findAll(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        Long localeIdFilter = ControllerSupport.queryLongOrNull(req, "localeId");
        if (localeIdFilter != null) {
            LocaleAccessRules.requireOwnerOrPlatformAdmin(user, localeService.requireLocale(localeIdFilter).ownerUserId());
        } else {
            RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        }
        res.type("application/json");
        return JsonSupport.toJson(edgeDeviceService.findAll(localeIdFilter, ControllerSupport.page(req)));
    }

    /**
     * Cerca dispositivi edge per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, edgeDeviceService.ownerOf(id));
        res.type("application/json");
        return JsonSupport.toJson(edgeDeviceService.findByIdDto(id));
    }

    /**
     * Restituisce lo stato del dispositivo edge.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object status(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, edgeDeviceService.ownerOf(id));
        res.type("application/json");
        return JsonSupport.toJson(edgeDeviceService.status(id));
    }

    /**
     * Aggiorna i parametri di configurazione del dispositivo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object updateConfig(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, edgeDeviceService.ownerOf(id));
        EdgeConfig config = JsonSupport.fromJson(req.body(), EdgeConfig.class);
        res.type("application/json");
        return JsonSupport.toJson(edgeDeviceService.updateConfig(id, config));
    }

    /**
     * Revoca il token o le credenziali indicati.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object revoke(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, edgeDeviceService.ownerOf(id));
        if (activeMatchQueryService != null) {
            var device = edgeDeviceService.requireEdgeDevice(id);
            activeMatchQueryService.requireNoActiveMatch(device.localeId(), device.dartboardId());
        }
        edgeDeviceService.revoke(id);
        res.status(204);
        return "";
    }

    /**
     * Verifica le credenziali e i dati necessari all'operazione.
     *
     * @param req richiesta HTTP ricevuta
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.AuthenticatedUser} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private AuthenticatedUser authenticate(Request req) {
        return RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
    }
}
