package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.MatchService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET /partite/{id/roster}: elenco participantId+ordine turno, interrogato dall'edge all'avvio e dopo una riconnessione.
 *
 * @see it.uniupo.pissir.darts.match.service.MatchService
 *  
 */
public final class RosterController {

    private final MatchService matchService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code RosterController} con i dati e le dipendenze indicati.
     *
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.MatchService
     *  
     */
    public RosterController(MatchService matchService, JwtTokenService jwtTokenService) {
        this.matchService = matchService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/partite/:id/roster", this::roster);
    }

    /**
     * Legge la formazione dei partecipanti della partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object roster(Request req, Response res) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RequestAuthentication.requireAnyRole(user, Role.EDGE_DEVICE.name(), "SERVICE");
        long matchId = ControllerSupport.pathLong(req, "id");
        Match match = matchService.requireMatch(matchId);
        if (!user.hasAnyRole("SERVICE")) RequestAuthentication.requireOwnDartboard(user, match.dartboardId());
        res.type("application/json");
        return JsonSupport.toJson(matchService.roster(matchId));
    }
}
