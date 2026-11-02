package it.uniupo.pissir.darts.user.controller;

import it.uniupo.pissir.darts.common.dto.HealthDTO;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.user.dto.ChangeApprovalStatusRequestDTO;
import it.uniupo.pissir.darts.user.dto.ChangePasswordRequestDTO;
import it.uniupo.pissir.darts.user.dto.ChangeRoleRequestDTO;
import it.uniupo.pissir.darts.user.dto.DeviceTokenRequestDTO;
import it.uniupo.pissir.darts.user.dto.DeviceTokenResponseDTO;
import it.uniupo.pissir.darts.user.dto.SetEnabledRequestDTO;
import it.uniupo.pissir.darts.user.dto.UpdateUserRequestDTO;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.security.UserAccessRules;
import it.uniupo.pissir.darts.user.service.DeviceTokenService;
import it.uniupo.pissir.darts.user.service.UserService;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * GET/PUT/DELETE /users*, PATCH /users/{id/password\|enabled\|role\|approvazione}, POST/DELETE /users/device-token*, GET /health.
 *
 * @see java.time.Instant
 *  
 */
public final class UserController {

    private static final Instant STARTED_AT = Instant.now();
    private static final String VERSION = "1.0.0-SNAPSHOT";

    private final UserService userService;
    private final DeviceTokenService deviceTokenService;
    private final JwtTokenService jwtTokenService;
    private final BooleanSupplier mqttConnected;

    /**
     * Inizializza {@code UserController} con i dati e le dipendenze indicati.
     *
     * @param userService componente {@link it.uniupo.pissir.darts.user.service.UserService} utilizzato dall'operazione
     * @param deviceTokenService componente {@link it.uniupo.pissir.darts.user.service.DeviceTokenService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.user.service.UserService
     *  
     */
    public UserController(UserService userService, DeviceTokenService deviceTokenService, JwtTokenService jwtTokenService) {
        this(userService, deviceTokenService, jwtTokenService, () -> false);
    }

    /**
     * Inizializza {@code UserController} con i dati e le dipendenze indicati.
     *
     * @param userService componente {@link it.uniupo.pissir.darts.user.service.UserService} utilizzato dall'operazione
     * @param deviceTokenService componente {@link it.uniupo.pissir.darts.user.service.DeviceTokenService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param mqttConnected stato della connessione MQTT
     * @see it.uniupo.pissir.darts.user.service.UserService
     *  
     */
    public UserController(UserService userService, DeviceTokenService deviceTokenService, JwtTokenService jwtTokenService,
                          BooleanSupplier mqttConnected) {
        this.userService = userService;
        this.deviceTokenService = deviceTokenService;
        this.jwtTokenService = jwtTokenService;
        this.mqttConnected = mqttConnected;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/users", this::findAll);

        Spark.get("/users/lookup", this::lookup);
        Spark.get("/users/:id", this::findById);
        Spark.put("/users/:id", this::update);
        Spark.patch("/users/:id/password", this::changePassword);
        Spark.patch("/users/:id/enabled", this::setEnabled);
        Spark.patch("/users/:id/role", this::changeRole);
        Spark.patch("/users/:id/approvazione", this::changeApprovalStatus);
        Spark.delete("/users/:id", this::delete);
        Spark.post("/users/device-token", this::issueDeviceToken);
        Spark.delete("/users/device-token/:deviceUserId", this::revokeDeviceToken);
        Spark.get("/health", this::health);
    }

    /**
     * Cerca utenti tutti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findAll(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        Page page = Page.of(parseIntOrNull(req.queryParams("page")), parseIntOrNull(req.queryParams("size")));
        Role roleFilter = parseRoleOrNull(req.queryParams("role"));
        ApprovalStatus approvalStatusFilter = parseApprovalStatusOrNull(req.queryParams("approvalStatus"));
        res.type("application/json");
        return JsonSupport.toJson(userService.findAll(page, roleFilter, approvalStatusFilter));
    }

    /**
     * Ricerca i dati essenziali del giocatore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Indicare uno tra i parametri: username, id»  
     *  
     */
    private Object lookup(Request req, Response res) {
        authenticate(req);
        String username = req.queryParams("username");
        String id = req.queryParams("id");
        res.type("application/json");
        if (username != null && !username.isBlank()) {
            return JsonSupport.toJson(userService.lookupPlayerByUsername(username));
        }
        if (id != null && !id.isBlank()) {
            return JsonSupport.toJson(userService.lookupPlayerById(parseId(id)));
        }
        throw new ValidationException("CAMPO_MANCANTE", "Indicare uno tra i parametri: username, id");
    }

    /**
     * Cerca utenti per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = pathId(req);
        UserAccessRules.requireSelfOrPlatformAdminOrService(user, id);
        res.type("application/json");
        return JsonSupport.toJson(userService.findByIdDto(id));
    }

    /**
     * Aggiorna l'utente tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = pathId(req);
        UserAccessRules.requireSelfOrPlatformAdmin(user, id);
        UpdateUserRequestDTO body = JsonSupport.fromJson(req.body(), UpdateUserRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(userService.update(id, body.email(), body.nickname(), body.avatarUrl(),
                body.firstName(), body.lastName()));
    }

    /**
     * Verifica la password attuale e registra quella nuova.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object changePassword(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = pathId(req);
        UserAccessRules.requireSelf(user, id);
        ChangePasswordRequestDTO body = JsonSupport.fromJson(req.body(), ChangePasswordRequestDTO.class);
        userService.changePassword(id, body.oldPassword(), body.newPassword());
        res.status(204);
        return "";
    }

    /**
     * Modifica l'abilitazione dell'account.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object setEnabled(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        long id = pathId(req);
        SetEnabledRequestDTO body = JsonSupport.fromJson(req.body(), SetEnabledRequestDTO.class);
        userService.setEnabled(id, body.enabled());
        res.status(204);
        return "";
    }

    /**
     * Aggiorna il ruolo dell'utente dopo i controlli di autorizzazione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object changeRole(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        long id = pathId(req);
        ChangeRoleRequestDTO body = JsonSupport.fromJson(req.body(), ChangeRoleRequestDTO.class);
        userService.changeRole(id, parseRole(body.role()));
        res.status(204);
        return "";
    }

    /**
     * Aggiorna lo stato di approvazione dell'account.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object changeApprovalStatus(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        long id = pathId(req);
        ChangeApprovalStatusRequestDTO body = JsonSupport.fromJson(req.body(), ChangeApprovalStatusRequestDTO.class);
        userService.changeApprovalStatus(id, parseApprovalStatus(body.stato()));
        res.status(204);
        return "";
    }

    /**
     * Elimina l'utente tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        userService.delete(pathId(req));
        res.status(204);
        return "";
    }

    /**
     * Emette le credenziali del dispositivo edge.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object issueDeviceToken(Request req, Response res) {
        DeviceTokenRequestDTO body = JsonSupport.fromJson(req.body(), DeviceTokenRequestDTO.class);
        var result = deviceTokenService.issue(body.localeId(), body.dartboardId(), body.serialNumber());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(new DeviceTokenResponseDTO(result.deviceUserId(), result.token()));
    }

    /**
     * Revoca le credenziali associate al dispositivo edge.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object revokeDeviceToken(Request req, Response res) {
        long deviceUserId = Long.parseLong(req.params(":deviceUserId"));
        deviceTokenService.revoke(deviceUserId);
        res.status(204);
        return "";
    }

    /**
     * Restituisce lo stato del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object health(Request req, Response res) {
        long uptimeSeconds = Instant.now().getEpochSecond() - STARTED_AT.getEpochSecond();
        res.type("application/json");
        return JsonSupport.toJson(new HealthDTO("user-service", VERSION, uptimeSeconds, HealthDTO.DATABASE_OK,
                mqttConnected.getAsBoolean() ? HealthDTO.MQTT_CONNESSO : HealthDTO.MQTT_DISCONNESSO));
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

    /**
     * Legge e valida l'identificativo nel percorso HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @return il valore numerico calcolato o letto  
     *  
     */
    private long pathId(Request req) {
        return parseId(req.params(":id"));
    }

    /**
     * Legge e converte identificativo dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return il valore numerico calcolato o letto
     *  
     */
    private long parseId(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Identificativo non valido");
        }
    }

    /**
     * Legge e converte int o assente dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return il valore numerico calcolato o letto
     *  
     */
    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Parametro di paginazione non valido: " + value);
        }
    }

    /**
     * Legge e converte ruolo o assente dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.Role} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.Role
     *  
     */
    private Role parseRoleOrNull(String value) {
        return value == null || value.isBlank() ? null : parseRole(value);
    }

    /**
     * Legge e converte ruolo dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.Role} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.Role
     *  
     */
    private Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("RUOLO_NON_VALIDO", "Ruolo non valido: " + value);
        }
    }

    /**
     * Legge e converte approval stato o assente dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.ApprovalStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.ApprovalStatus
     *  
     */
    private ApprovalStatus parseApprovalStatusOrNull(String value) {
        return value == null || value.isBlank() ? null : parseApprovalStatus(value);
    }

    /**
     * Legge e converte approval stato dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.ApprovalStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.ApprovalStatus
     *  
     */
    private ApprovalStatus parseApprovalStatus(String value) {
        try {
            return ApprovalStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("STATO_APPROVAZIONE_NON_VALIDO", "Stato di approvazione non valido: " + value);
        }
    }
}
