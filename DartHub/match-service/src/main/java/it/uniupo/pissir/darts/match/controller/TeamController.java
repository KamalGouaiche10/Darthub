package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.dto.AddTeamMemberRequestDTO;
import it.uniupo.pissir.darts.match.dto.CreateTeamRequestDTO;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.TeamService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET/POST /squadre, GET/PUT/DELETE /squadre/{id}, POST/DELETE /squadre/{id/membri[/{playerId}]}.
 *
 * @see it.uniupo.pissir.darts.match.service.TeamService
 *  
 */
public final class TeamController {

    private final TeamService teamService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code TeamController} con i dati e le dipendenze indicati.
     *
     * @param teamService componente {@link it.uniupo.pissir.darts.match.service.TeamService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.TeamService
     *  
     */
    public TeamController(TeamService teamService, JwtTokenService jwtTokenService) {
        this.teamService = teamService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/squadre", this::findAll);
        Spark.post("/squadre", this::create);
        Spark.get("/squadre/:id", this::findById);
        Spark.put("/squadre/:id", this::update);
        Spark.delete("/squadre/:id", this::delete);
        Spark.post("/squadre/:id/membri", this::addMember);
        Spark.delete("/squadre/:id/membri/:playerId", this::removeMember);
    }

    /**
     * Cerca squadre tutti.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findAll(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(teamService.findAll(ControllerSupport.page(req)));
    }

    /**
     * Crea la squadra tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        CreateTeamRequestDTO body = JsonSupport.fromJson(req.body(), CreateTeamRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(teamService.create(body.nome(), user.userId()));
    }

    /**
     * Cerca squadre per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(teamService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Aggiorna la squadra tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        CreateTeamRequestDTO body = JsonSupport.fromJson(req.body(), CreateTeamRequestDTO.class);
        res.type("application/json");
        return JsonSupport.toJson(teamService.update(id, body.nome()));
    }

    /**
     * Elimina la squadra tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        authenticate(req);
        teamService.delete(ControllerSupport.pathLong(req, "id"));
        res.status(204);
        return "";
    }

    /**
     * Aggiunge un giocatore alla squadra.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: playerId»  
     *  
     */
    private Object addMember(Request req, Response res) {
        authenticate(req);
        long teamId = ControllerSupport.pathLong(req, "id");
        AddTeamMemberRequestDTO body = JsonSupport.fromJson(req.body(), AddTeamMemberRequestDTO.class);
        if (body.playerId() == null) {
            throw new ValidationException("Campo obbligatorio mancante: playerId");
        }
        teamService.addMember(teamId, body.playerId());
        res.status(204);
        return "";
    }

    /**
     * Rimuove un giocatore dalla squadra.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object removeMember(Request req, Response res) {
        authenticate(req);
        long teamId = ControllerSupport.pathLong(req, "id");
        long playerId = ControllerSupport.pathLong(req, "playerId");
        teamService.removeMember(teamId, playerId);
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
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name(), "SERVICE");
        return user;
    }
}
