package it.uniupo.pissir.darts.game.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.game.dto.CreateSensorTypeRequestDTO;
import it.uniupo.pissir.darts.game.dto.UpdateSensorTypeRequestDTO;
import it.uniupo.pissir.darts.game.model.Role;
import it.uniupo.pissir.darts.game.service.SensorTypeService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /giochi/{id/sensori}, GET/PUT/DELETE /sensori/{id} — tutte riservate a GAME_ADMIN.
 *
 * @see it.uniupo.pissir.darts.game.service.SensorTypeService
 *  
 */
public final class SensorTypeController {

    private final SensorTypeService sensorTypeService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code SensorTypeController} con i dati e le dipendenze indicati.
     *
     * @param sensorTypeService componente {@link it.uniupo.pissir.darts.game.service.SensorTypeService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.game.service.SensorTypeService
     *  
     */
    public SensorTypeController(SensorTypeService sensorTypeService, JwtTokenService jwtTokenService) {
        this.sensorTypeService = sensorTypeService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/giochi/:gameTypeId/sensori", this::findByGameType);
        Spark.post("/giochi/:gameTypeId/sensori", this::create);
        Spark.get("/sensori/:id", this::findById);
        Spark.put("/sensori/:id", this::update);
        Spark.delete("/sensori/:id", this::delete);
    }

    /**
     * Cerca tipi di sensore per gioco tipo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findByGameType(Request req, Response res) {
        requireGameAdmin(req);
        long gameTypeId = ControllerSupport.pathLong(req, "gameTypeId");
        res.type("application/json");
        return JsonSupport.toJson(sensorTypeService.findByGameType(gameTypeId, ControllerSupport.page(req)));
    }

    /**
     * Crea il tipo di sensore tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        requireGameAdmin(req);
        long gameTypeId = ControllerSupport.pathLong(req, "gameTypeId");
        CreateSensorTypeRequestDTO body = JsonSupport.fromJson(req.body(), CreateSensorTypeRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(sensorTypeService.create(gameTypeId, body.nome(), body.formatoDato(), body.modalita()));
    }

    /**
     * Cerca tipi di sensore per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        requireGameAdmin(req);
        res.type("application/json");
        return JsonSupport.toJson(sensorTypeService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Aggiorna il tipo di sensore tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        requireGameAdmin(req);
        long id = ControllerSupport.pathLong(req, "id");
        UpdateSensorTypeRequestDTO body = JsonSupport.fromJson(req.body(), UpdateSensorTypeRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(sensorTypeService.update(id, body.nome(), body.formatoDato(), body.modalita()));
    }

    /**
     * Elimina il tipo di sensore tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        requireGameAdmin(req);
        sensorTypeService.delete(ControllerSupport.pathLong(req, "id"));
        res.status(204);
        return "";
    }

    /**
     * Verifica il requisito gioco amministratore prima di eseguire l'operazione.
     *
     * @param req richiesta HTTP ricevuta  
     *  
     */
    private void requireGameAdmin(Request req) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RequestAuthentication.requireAnyRole(user, Role.GAME_ADMIN.name());
    }
}
