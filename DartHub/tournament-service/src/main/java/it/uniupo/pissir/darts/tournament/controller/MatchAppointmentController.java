package it.uniupo.pissir.darts.tournament.controller;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.tournament.service.MatchAppointmentService;
import java.time.Instant;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * Espone le operazioni HTTP relative a appuntamenti delle partite.
 *
 * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService
 *  
 */
public final class MatchAppointmentController {
    /**
     * Raccoglie i dati della richiesta relativa a schedule.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param scheduledAt istante programmato per l'inizio della partita
     * @param durationMinutes durata della partita in minuti
     *  
     */
    private record ScheduleRequest(String scheduledAt, Integer durationMinutes) { }
    private final MatchAppointmentService service;
    private final JwtTokenService jwt;
    /**
     * Inizializza {@code MatchAppointmentController} con i dati e le dipendenze indicati.
     *
     * @param service servizio da utilizzare
     * @param jwt token JWT da elaborare
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService
     *  
     */
    public MatchAppointmentController(MatchAppointmentService service, JwtTokenService jwt) {
        this.service = service; this.jwt = jwt;
    }
    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/tornei/:id/partite", this::list);
        Spark.put("/tornei/:id/partite/:matchId/orario", this::schedule);
        Spark.get("/tornei/partite/:matchId/appuntamento", this::view);
        Spark.post("/tornei/partite/:matchId/pronto", this::ready);
        Spark.post("/tornei/partite/:matchId/accetta", this::accept);
        Spark.get("/tornei/partite/:matchId/accesso", this::access);
    }
    /**
     * Elenca l'appuntamento della partita tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object list(Request req, Response res) {
        RequestAuthentication.requireAnyRole(user(req), "GAME_ADMIN", "PLATFORM_ADMIN");
        res.type("application/json");
        return JsonSupport.toJson(service.matches(Long.parseLong(req.params("id"))));
    }
    /**
     * Pianifica l'appuntamento della partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «scheduledAt obbligatorio»  
     *  
     */
    private Object schedule(Request req, Response res) {
        RequestAuthentication.requireAnyRole(user(req), "GAME_ADMIN", "PLATFORM_ADMIN");
        ScheduleRequest body = JsonSupport.fromJson(req.body(), ScheduleRequest.class);
        if (body == null || body.scheduledAt() == null) throw new ValidationException("scheduledAt obbligatorio");
        Instant at;
        try { at = Instant.parse(body.scheduledAt()); }
        catch (RuntimeException e) { throw new ValidationException("scheduledAt deve includere il fuso orario in formato UTC"); }
        res.type("application/json");
        return JsonSupport.toJson(service.schedule(Long.parseLong(req.params("id")),
                Long.parseLong(req.params("matchId")), at,
                body.durationMinutes() == null ? 60 : body.durationMinutes()));
    }
    /**
     * Compone i dati dell'appuntamento per la risposta.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object view(Request req, Response res) {
        AuthenticatedUser u = user(req);
        RequestAuthentication.requireAnyRole(u, "PLAYER", "GAME_ADMIN", "PLATFORM_ADMIN");
        long id = Long.parseLong(req.params("matchId"));
        res.type("application/json");
        if (u.hasAnyRole("PLAYER")) return JsonSupport.toJson(service.viewForPlayer(id, u.userId()));
        res.type("application/json");
        return JsonSupport.toJson(service.view(id));
    }
    /**
     * Registra la presenza del giocatore all'appuntamento.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object ready(Request req, Response res) {
        AuthenticatedUser u = user(req);
        RequestAuthentication.requireAnyRole(u, "PLAYER");
        res.type("application/json");
        return JsonSupport.toJson(service.ready(Long.parseLong(req.params("matchId")), u.userId()));
    }
    /**
     * Registra l'accettazione dell'appuntamento da parte del giocatore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object accept(Request req, Response res) {
        AuthenticatedUser u = user(req);
        RequestAuthentication.requireAnyRole(u, "PLAYER");
        res.type("application/json");
        return JsonSupport.toJson(service.accept(Long.parseLong(req.params("matchId")), u.userId()));
    }
    /**
     * Verifica l'accesso del giocatore all'appuntamento della partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object access(Request req, Response res) {
        AuthenticatedUser u = user(req);
        RequestAuthentication.requireAnyRole(u, "PLAYER");
        res.type("application/json");
        return JsonSupport.toJson(service.access(Long.parseLong(req.params("matchId")), u.userId()));
    }
    /**
     * Risolve l'utente autenticato della richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.AuthenticatedUser} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private AuthenticatedUser user(Request req) { return RequestAuthentication.resolve(req.headers("Authorization"), jwt); }
}
