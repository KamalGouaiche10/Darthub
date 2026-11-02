package it.uniupo.pissir.darts.locale.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.locale.dto.CreateDartboardRequestDTO;
import it.uniupo.pissir.darts.locale.dto.SetDartboardStatusRequestDTO;
import it.uniupo.pissir.darts.locale.dto.UpdateDartboardRequestDTO;
import it.uniupo.pissir.darts.locale.security.LocaleAccessRules;
import it.uniupo.pissir.darts.locale.service.DartboardService;
import it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /locali/{id/bersagli}, GET/PUT/DELETE /bersagli/{id}, PATCH /bersagli/{id/stato}.
 *
 * @see it.uniupo.pissir.darts.locale.service.DartboardService
 *  
 */
public final class DartboardController {

    private final DartboardService dartboardService;
    private final LocaleService localeService;
    private final JwtTokenService jwtTokenService;
    private final ActiveMatchQueryService activeMatchQueryService;

    /**
     * Inizializza {@code DartboardController} con i dati e le dipendenze indicati.
     *
     * @param dartboardService componente {@link it.uniupo.pissir.darts.locale.service.DartboardService} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.locale.service.DartboardService
     *  
     */
    public DartboardController(DartboardService dartboardService, LocaleService localeService,
                                JwtTokenService jwtTokenService) {
        this(dartboardService, localeService, jwtTokenService, null);
    }

    /**
     * Inizializza {@code DartboardController} con i dati e le dipendenze indicati.
     *
     * @param dartboardService componente {@link it.uniupo.pissir.darts.locale.service.DartboardService} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param activeMatchQueryService componente {@link it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.service.DartboardService
     *  
     */
    public DartboardController(DartboardService dartboardService, LocaleService localeService,
                                JwtTokenService jwtTokenService, ActiveMatchQueryService activeMatchQueryService) {
        this.dartboardService = dartboardService;
        this.localeService = localeService;
        this.jwtTokenService = jwtTokenService;
        this.activeMatchQueryService = activeMatchQueryService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/locali/:localeId/bersagli", this::findByLocale);
        Spark.post("/locali/:localeId/bersagli", this::create);
        Spark.get("/bersagli/:id", this::findById);
        Spark.put("/bersagli/:id", this::update);
        Spark.delete("/bersagli/:id", this::delete);
        Spark.patch("/bersagli/:id/stato", this::setStato);
    }

    /**
     * Cerca bersagli per locale.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findByLocale(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long localeId = ControllerSupport.pathLong(req, "localeId");
        LocaleAccessRules.requireVisible(user, localeService.requireLocale(localeId));
        res.type("application/json");
        return JsonSupport.toJson(dartboardService.findByLocale(localeId, ControllerSupport.page(req)));
    }

    /**
     * Crea il bersaglio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long localeId = ControllerSupport.pathLong(req, "localeId");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, localeService.requireLocale(localeId).ownerUserId());
        CreateDartboardRequestDTO body = JsonSupport.fromJson(req.body(), CreateDartboardRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(dartboardService.create(localeId, body.identificativoLocale(), body.gameTypeId()));
    }

    /**
     * Cerca bersagli per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        long localeId = dartboardService.findByIdDto(id).localeId();
        LocaleAccessRules.requireVisible(user, localeService.requireLocale(localeId));
        res.type("application/json");
        return JsonSupport.toJson(dartboardService.findByIdDto(id));
    }

    /**
     * Aggiorna il bersaglio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, dartboardService.ownerOf(id));
        requireNoActiveMatch(id);
        UpdateDartboardRequestDTO body = JsonSupport.fromJson(req.body(), UpdateDartboardRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(dartboardService.update(id, body.identificativoLocale(), body.gameTypeId()));
    }

    /**
     * Elimina il bersaglio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, dartboardService.ownerOf(id));
        requireNoActiveMatch(id);
        dartboardService.delete(id);
        res.status(204);
        return "";
    }

    /**
     * Imposta stato per il componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ConflictException se viene rilevato l'errore «IN_USO è impostato solo dalla prenotazione di una partita»  
     *  
     */
    private Object setStato(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdminOrService(user, dartboardService.ownerOf(id));
        SetDartboardStatusRequestDTO body = JsonSupport.fromJson(req.body(), SetDartboardStatusRequestDTO.class);
        if ("IN_USO".equals(body.stato())) {
            throw new ConflictException("STATO_RISERVATO", "IN_USO è impostato solo dalla prenotazione di una partita");
        }
        requireNoActiveMatch(id);
        res.type("application/json");
        return JsonSupport.toJson(dartboardService.setStato(id, body.stato()));
    }

    /**
     * Impedisce la modifica del bersaglio quando esiste una partita attiva.
     *
     * @param id identificativo del bersaglio
     *  
     */
    private void requireNoActiveMatch(long id) {
        if (activeMatchQueryService != null) {
            var board = dartboardService.requireDartboard(id);
            activeMatchQueryService.requireNoActiveMatch(board.localeId(), id);
        }
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
