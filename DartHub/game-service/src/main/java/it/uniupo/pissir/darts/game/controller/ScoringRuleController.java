package it.uniupo.pissir.darts.game.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.game.dto.CreateScoringRuleRequestDTO;
import it.uniupo.pissir.darts.game.dto.UpdateScoringRuleRequestDTO;
import it.uniupo.pissir.darts.game.model.Role;
import it.uniupo.pissir.darts.game.service.ScoringRuleService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /giochi/{id/regole} (lettura PLAYER, scrittura GAME_ADMIN), GET/PUT/DELETE /regole/{id} (tutte GAME_ADMIN) — 3, rotte 28-29.
 *
 * @see it.uniupo.pissir.darts.game.service.ScoringRuleService
 *  
 */
public final class ScoringRuleController {

    private final ScoringRuleService scoringRuleService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code ScoringRuleController} con i dati e le dipendenze indicati.
     *
     * @param scoringRuleService componente {@link it.uniupo.pissir.darts.game.service.ScoringRuleService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.game.service.ScoringRuleService
     *  
     */
    public ScoringRuleController(ScoringRuleService scoringRuleService, JwtTokenService jwtTokenService) {
        this.scoringRuleService = scoringRuleService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/giochi/:gameTypeId/regole", this::findByGameType);
        Spark.post("/giochi/:gameTypeId/regole", this::create);
        Spark.get("/regole/:id", this::findById);
        Spark.put("/regole/:id", this::update);
        Spark.delete("/regole/:id", this::delete);
    }

    /**
     * Cerca regole di punteggio per gioco tipo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findByGameType(Request req, Response res) {
        authenticate(req);
        long gameTypeId = ControllerSupport.pathLong(req, "gameTypeId");
        res.type("application/json");
        return JsonSupport.toJson(scoringRuleService.findByGameType(gameTypeId, ControllerSupport.page(req)));
    }

    /**
     * Crea la regola di punteggio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        requireGameAdmin(req);
        long gameTypeId = ControllerSupport.pathLong(req, "gameTypeId");
        CreateScoringRuleRequestDTO body = JsonSupport.fromJson(req.body(), CreateScoringRuleRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(scoringRuleService.create(gameTypeId, body.variante(), body.direzione(),
                body.punteggioIniziale(), body.punteggioObiettivo(), body.chiusuraDouble(), body.bustAnnullaTurno(),
                body.maxTiriPerTurno(), body.parametriJson()));
    }

    /**
     * Cerca regole di punteggio per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        RequestAuthentication.requireAnyRole(authenticate(req), Role.GAME_ADMIN.name(), "SERVICE");
        res.type("application/json");
        return JsonSupport.toJson(scoringRuleService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Aggiorna la regola di punteggio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        requireGameAdmin(req);
        long id = ControllerSupport.pathLong(req, "id");
        UpdateScoringRuleRequestDTO body = JsonSupport.fromJson(req.body(), UpdateScoringRuleRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(scoringRuleService.update(id, body.variante(), body.direzione(), body.punteggioIniziale(),
                body.punteggioObiettivo(), body.chiusuraDouble(), body.bustAnnullaTurno(), body.maxTiriPerTurno(),
                body.parametriJson()));
    }

    /**
     * Elimina la regola di punteggio tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        requireGameAdmin(req);
        scoringRuleService.delete(ControllerSupport.pathLong(req, "id"));
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

    /**
     * Verifica il requisito gioco amministratore prima di eseguire l'operazione.
     *
     * @param req richiesta HTTP ricevuta  
     *  
     */
    private void requireGameAdmin(Request req) {
        RequestAuthentication.requireAnyRole(authenticate(req), Role.GAME_ADMIN.name());
    }
}
