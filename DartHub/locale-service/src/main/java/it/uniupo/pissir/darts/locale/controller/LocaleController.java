package it.uniupo.pissir.darts.locale.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.locale.dto.ChangeLocaleStatusRequestDTO;
import it.uniupo.pissir.darts.locale.dto.CreateLocaleRequestDTO;
import it.uniupo.pissir.darts.locale.dto.UpdateLocaleRequestDTO;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.Role;
import it.uniupo.pissir.darts.locale.security.LocaleAccessRules;
import it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /locali, GET/PUT/DELETE /locali/{id}, PATCH /locali/{id/stato}, GET /locali/{id/partite-attive}.
 *
 * @see it.uniupo.pissir.darts.locale.service.LocaleService
 *  
 */
public final class LocaleController {

    private final LocaleService localeService;
    private final ActiveMatchQueryService activeMatchQueryService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code LocaleController} con i dati e le dipendenze indicati.
     *
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param activeMatchQueryService componente {@link it.uniupo.pissir.darts.locale.service.ActiveMatchQueryService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.locale.service.LocaleService
     *  
     */
    public LocaleController(LocaleService localeService, ActiveMatchQueryService activeMatchQueryService,
                             JwtTokenService jwtTokenService) {
        this.localeService = localeService;
        this.activeMatchQueryService = activeMatchQueryService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/locali", this::findAll);
        Spark.post("/locali", this::create);
        Spark.get("/locali/:id", this::findById);
        Spark.put("/locali/:id", this::update);
        Spark.patch("/locali/:id/stato", this::changeStato);
        Spark.delete("/locali/:id", this::delete);
        Spark.get("/locali/:id/partite-attive", this::activeMatches);
    }

    /**
     * Cerca locali tutti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findAll(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        Long ownerFilter = ControllerSupport.queryLongOrNull(req, "ownerUserId");
        LocaleStatus statoFilter = parseStatoOrNull(req.queryParams("stato"));
        boolean isAdmin = user.hasAnyRole(Role.PLATFORM_ADMIN.name());
        boolean vedeTutti = isAdmin || (user.hasAnyRole(Role.LOCALE_ADMIN.name())
                && ownerFilter != null && ownerFilter == user.userId());
        LocaleStatus statoEffettivo = vedeTutti ? statoFilter : LocaleStatus.APPROVATO;
        res.type("application/json");
        return JsonSupport.toJson(localeService.search(ControllerSupport.page(req), ownerFilter, statoEffettivo));
    }

    /**
     * Crea il locale tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: ownerUserId»  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.LOCALE_ADMIN.name());
        boolean isAdmin = user.hasAnyRole(Role.PLATFORM_ADMIN.name());
        CreateLocaleRequestDTO body = JsonSupport.fromJson(req.body(), CreateLocaleRequestDTO.class);
        res.type("application/json");
        if (isAdmin) {
            if (body.ownerUserId() == null) {
                throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: ownerUserId");
            }
            res.status(201);
            return JsonSupport.toJson(localeService.create(body.nome(), body.indirizzo(), body.tipo(),
                    body.ownerUserId(), LocaleStatus.APPROVATO));
        } else {
            res.status(201);
            return JsonSupport.toJson(localeService.create(body.nome(), body.indirizzo(), body.tipo(),
                    user.userId(), LocaleStatus.IN_ATTESA));
        }
    }

    /**
     * Aggiorna lo stato della risorsa dopo i controlli previsti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object changeStato(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        ChangeLocaleStatusRequestDTO body = JsonSupport.fromJson(req.body(), ChangeLocaleStatusRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(localeService.changeStato(id, parseStato(body.stato())));
    }

    /**
     * Legge e converte stato o assente dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.LocaleStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.LocaleStatus
     *  
     */
    private LocaleStatus parseStatoOrNull(String value) {
        return value == null || value.isBlank() ? null : parseStato(value);
    }

    /**
     * Legge e converte stato dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.LocaleStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.LocaleStatus
     *  
     */
    private LocaleStatus parseStato(String value) {
        try {
            return LocaleStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("STATO_NON_VALIDO", "Stato non valido: " + value);
        }
    }

    /**
     * Cerca locali per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireVisible(user, localeService.requireLocale(id));
        res.type("application/json");
        return JsonSupport.toJson(localeService.findByIdDto(id));
    }

    /**
     * Aggiorna il locale tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, localeService.requireLocale(id).ownerUserId());
        UpdateLocaleRequestDTO body = JsonSupport.fromJson(req.body(), UpdateLocaleRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(localeService.update(id, body.nome(), body.indirizzo(), body.tipo()));
    }

    /**
     * Elimina il locale tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, localeService.requireLocale(id).ownerUserId());
        localeService.delete(id);
        res.status(204);
        return "";
    }

    /**
     * Recupera le partite attive del locale.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object activeMatches(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        LocaleAccessRules.requireOwnerOrPlatformAdmin(user, localeService.requireLocale(id).ownerUserId());
        res.type("application/json");
        return JsonSupport.toJson(activeMatchQueryService.activeMatches(id));
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
