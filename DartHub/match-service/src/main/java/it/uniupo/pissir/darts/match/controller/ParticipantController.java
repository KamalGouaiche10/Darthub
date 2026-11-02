package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.dto.AddParticipantRequestDTO;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.MatchService;
import it.uniupo.pissir.darts.match.service.ParticipantService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * POST /partite/{id/partecipanti}, DELETE /partite/{id/partecipanti/{pid}}.
 *
 * @see it.uniupo.pissir.darts.match.service.ParticipantService
 *  
 */
public final class ParticipantController {

    private final ParticipantService participantService;
    private final MatchService matchService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code ParticipantController} con i dati e le dipendenze indicati.
     *
     * @param participantService componente {@link it.uniupo.pissir.darts.match.service.ParticipantService} utilizzato dall'operazione
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.ParticipantService
     *  
     */
    public ParticipantController(ParticipantService participantService, MatchService matchService,
                                 JwtTokenService jwtTokenService) {
        this.participantService = participantService;
        this.matchService = matchService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/partite/:id/partecipanti", this::add);
        Spark.delete("/partite/:id/partecipanti/:pid", this::remove);
    }

    /**
     * Aggiunge il partecipante alla partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object add(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name(), "SERVICE");
        long matchId = ControllerSupport.pathLong(req, "id");
        requireCreatorOrService(user, matchId);
        AddParticipantRequestDTO body = JsonSupport.fromJson(req.body(), AddParticipantRequestDTO.class);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(participantService.add(matchId, body.playerId(), body.teamId()));
    }

    /**
     * Rimuove il partecipante verificando i vincoli dell'operazione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object remove(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long matchId = ControllerSupport.pathLong(req, "id");
        requireCreatorOrService(user, matchId);
        long participantId = ControllerSupport.pathLong(req, "pid");
        participantService.remove(matchId, participantId);
        res.status(204);
        return "";
    }

    /**
     * Verifica il requisito creator o servizio prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param matchId identificativo della partita
     * @throws ForbiddenException se viene rilevato l'errore «Solo il creatore può modificare i partecipanti»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private void requireCreatorOrService(AuthenticatedUser user, long matchId) {
        if (!user.hasAnyRole("SERVICE") && matchService.requireMatch(matchId).createdBy() != user.userId()) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Solo il creatore può modificare i partecipanti");
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
