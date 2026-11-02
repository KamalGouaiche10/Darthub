package it.uniupo.pissir.darts.user.controller;

import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtSigningKeyStore;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.user.dto.LoginRequestDTO;
import it.uniupo.pissir.darts.user.dto.RefreshRequestDTO;
import it.uniupo.pissir.darts.user.dto.RegisterRequestDTO;
import it.uniupo.pissir.darts.user.dto.ServiceTokenRequestDTO;
import it.uniupo.pissir.darts.user.dto.ServiceTokenResponseDTO;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.service.AuthService;
import it.uniupo.pissir.darts.user.service.UserService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * POST /auth/register · /auth/login · /auth/refresh · /auth/logout più GET /auth/jwks.json e POST /auth/service-token: user-service è l'unico Authorization Server della piattaforma (§14).
 *
 * @see it.uniupo.pissir.darts.user.service.AuthService
 *  
 */
public final class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;
    private final JwtSigningKeyStore jwtSigningKeyStore;
    private final String serviceClientSecret;

    /**
     * Inizializza {@code AuthController} con i dati e le dipendenze indicati.
     *
     * @param authService componente {@link it.uniupo.pissir.darts.user.service.AuthService} utilizzato dall'operazione
     * @param userService componente {@link it.uniupo.pissir.darts.user.service.UserService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param jwtConfig configurazione dei token JWT
     * @param jwtSigningKeyStore JWT signing key store da utilizzare
     * @param serviceClientSecret credenziale delle chiamate fra servizi
     * @see it.uniupo.pissir.darts.user.service.AuthService
     *  
     */
    public AuthController(AuthService authService, UserService userService, JwtTokenService jwtTokenService,
                           JwtConfig jwtConfig, JwtSigningKeyStore jwtSigningKeyStore, String serviceClientSecret) {
        this.authService = authService;
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.jwtConfig = jwtConfig;
        this.jwtSigningKeyStore = jwtSigningKeyStore;
        this.serviceClientSecret = serviceClientSecret;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/auth/register", this::register);
        Spark.post("/auth/login", this::login);
        Spark.post("/auth/refresh", this::refresh);
        Spark.post("/auth/logout", this::logout);
        Spark.get("/auth/jwks.json", this::jwks);
        Spark.post("/auth/service-token", this::serviceToken);
    }

    /**
     * Restituisce le chiavi pubbliche di verifica dei JWT.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object jwks(Request req, Response res) {
        res.type("application/json");
        return jwtSigningKeyStore.publicJwkSetJson();
    }

    /**
     * Ottiene il token per la chiamata fra servizi.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws UnauthorizedException se viene rilevato l'errore «Credenziali di servizio non valide»  
     *  
     */
    private Object serviceToken(Request req, Response res) {
        ServiceTokenRequestDTO body = JsonSupport.fromJson(req.body(), ServiceTokenRequestDTO.class);
        if (body.clientSecret() == null || !body.clientSecret().equals(serviceClientSecret)
                || body.clientId() == null || body.clientId().isBlank()) {
            throw new UnauthorizedException("Credenziali di servizio non valide");
        }
        String accessToken = jwtTokenService.generateAccessToken(0, body.clientId(), "SERVICE");
        res.type("application/json");
        return JsonSupport.toJson(new ServiceTokenResponseDTO(accessToken, "Bearer", jwtConfig.accessTokenTtl().toSeconds()));
    }

    /**
     * Registra gli endpoint HTTP del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object register(Request req, Response res) {
        RegisterRequestDTO body = JsonSupport.fromJson(req.body(), RegisterRequestDTO.class);
        Role role = parseRoleOrNull(body.role());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(userService.register(body.username(), body.email(), body.password(), body.nickname(),
                role, body.firstName(), body.lastName()));
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
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("RUOLO_NON_VALIDO", "Ruolo non valido: " + value);
        }
    }

    /**
     * Verifica le credenziali ed emette i token della sessione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return i token di accesso e aggiornamento della sessione autenticata  
     *  
     */
    private Object login(Request req, Response res) {
        LoginRequestDTO body = JsonSupport.fromJson(req.body(), LoginRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(authService.login(body.username(), body.password()));
    }

    /**
     * Verifica il token di aggiornamento e rinnova la sessione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return i token della sessione rinnovata  
     *  
     */
    private Object refresh(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RefreshRequestDTO body = JsonSupport.fromJson(req.body(), RefreshRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(authService.refresh(body.refreshToken()));
    }

    /**
     * Revoca il token di aggiornamento della sessione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object logout(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RefreshRequestDTO body = JsonSupport.fromJson(req.body(), RefreshRequestDTO.class);
        authService.logout(body.refreshToken());
        res.status(204);
        return "";
    }
}
