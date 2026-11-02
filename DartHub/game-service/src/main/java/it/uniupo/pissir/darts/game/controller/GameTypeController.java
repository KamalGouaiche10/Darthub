package it.uniupo.pissir.darts.game.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.game.dto.CreateGameTypeRequestDTO;
import it.uniupo.pissir.darts.game.dto.UpdateGameTypeRequestDTO;
import it.uniupo.pissir.darts.game.model.Role;
import it.uniupo.pissir.darts.game.service.GameTypeService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /giochi, GET/PUT/DELETE /giochi/{id}.
 *
 * @see it.uniupo.pissir.darts.game.service.GameTypeService
 *  
 */
public final class GameTypeController {

    private final GameTypeService gameTypeService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code GameTypeController} con i dati e le dipendenze indicati.
     *
     * @param gameTypeService componente {@link it.uniupo.pissir.darts.game.service.GameTypeService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.game.service.GameTypeService
     *  
     */
    public GameTypeController(GameTypeService gameTypeService, JwtTokenService jwtTokenService) {
        this.gameTypeService = gameTypeService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/giochi", this::findAll);
        Spark.post("/giochi", this::create);
        Spark.get("/giochi/:id", this::findById);
        Spark.put("/giochi/:id", this::update);
        Spark.delete("/giochi/:id", this::delete);
    }

    /**
     * Cerca giochi tutti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findAll(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(gameTypeService.findAll(ControllerSupport.page(req)));
    }

    /**
     * Crea il gioco tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.GAME_ADMIN.name());
        CreateGameTypeRequestDTO body = JsonSupport.fromJson(req.body(), CreateGameTypeRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(gameTypeService.create(body.nome(), body.descrizione(), body.motore()));
    }

    /**
     * Cerca giochi per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(gameTypeService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Aggiorna il gioco tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        UpdateGameTypeRequestDTO body = JsonSupport.fromJson(req.body(), UpdateGameTypeRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(gameTypeService.update(id, body.nome(), body.descrizione(), body.motore()));
    }

    /**
     * Elimina il gioco tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.GAME_ADMIN.name());
        gameTypeService.delete(ControllerSupport.pathLong(req, "id"));
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
