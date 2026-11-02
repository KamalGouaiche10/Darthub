package it.uniupo.pissir.darts.tournament.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.tournament.dto.AddLocaleRequestDTO;
import it.uniupo.pissir.darts.tournament.dto.AddMatchRequestDTO;
import it.uniupo.pissir.darts.tournament.dto.CreateTournamentRequestDTO;
import it.uniupo.pissir.darts.tournament.dto.EnrollRequestDTO;
import it.uniupo.pissir.darts.tournament.dto.UpdateTournamentRequestDTO;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Role;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.service.EnrollmentService;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.service.PairingService;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Map;

/**
 * GET/POST /tornei, GET/PUT/DELETE /tornei/{id}, POST/DELETE /tornei/{id/locali[/{localeId}]}, POST/DELETE /tornei/{id/iscrizioni[/{pid}]}, POST/DELETE /tornei/{id/partite[/{matchId}]}, POST /tornei/{id/calendario}, POST /tornei/{id/chiudi}.
 *
 * @see it.uniupo.pissir.darts.tournament.service.TournamentService
 *  
 */
public final class TournamentController {

    private final TournamentService tournamentService;
    private final EnrollmentService enrollmentService;
    private final ScheduleGenerator scheduleGenerator;
    private final JwtTokenService jwtTokenService;
    private final PairingService pairingService;

    /**
     * Inizializza {@code TournamentController} con i dati e le dipendenze indicati.
     *
     * @param tournamentService componente {@link it.uniupo.pissir.darts.tournament.service.TournamentService} utilizzato dall'operazione
     * @param enrollmentService componente {@link it.uniupo.pissir.darts.tournament.service.EnrollmentService} utilizzato dall'operazione
     * @param scheduleGenerator componente {@link it.uniupo.pissir.darts.tournament.service.ScheduleGenerator} utilizzato dall'operazione
     * @param pairingService componente {@link it.uniupo.pissir.darts.tournament.service.PairingService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.tournament.service.TournamentService
     *  
     */
    public TournamentController(TournamentService tournamentService, EnrollmentService enrollmentService,
                                 ScheduleGenerator scheduleGenerator, PairingService pairingService,
                                 JwtTokenService jwtTokenService) {
        this.tournamentService = tournamentService;
        this.enrollmentService = enrollmentService;
        this.scheduleGenerator = scheduleGenerator;
        this.pairingService = pairingService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/tornei", this::search);
        Spark.get("/tornei/mie-iscrizioni", this::myEnrollments);
        Spark.get("/tornei/miei-abbinamenti", this::myPairings);
        Spark.post("/tornei/avvisi/:matchId/letto", this::markNoticeRead);
        Spark.post("/tornei", this::create);
        Spark.get("/tornei/:id", this::findById);
        Spark.put("/tornei/:id", this::update);
        Spark.delete("/tornei/:id", this::delete);
        Spark.post("/tornei/:id/locali", this::addLocale);
        Spark.delete("/tornei/:id/locali/:localeId", this::removeLocale);
        Spark.post("/tornei/:id/iscrizioni", this::enroll);
        Spark.delete("/tornei/:id/iscrizioni/:pid", this::withdraw);
        Spark.get("/tornei/:id/iscrizioni", this::findEnrollments);
        Spark.post("/tornei/:id/partite", this::addMatch);
        Spark.delete("/tornei/:id/partite/:matchId", this::removeMatch);
        Spark.post("/tornei/:id/calendario", this::generaCalendario);
        Spark.post("/tornei/:id/chiudi", this::chiudi);
    }

    /**
     * Legge gli abbinamenti dell'utente autenticato.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object myPairings(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        res.type("application/json");
        return JsonSupport.toJson(pairingService.forPlayer(user.userId()));
    }

    /**
     * Legge le iscrizioni dell'utente autenticato.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object myEnrollments(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        res.type("application/json");
        return JsonSupport.toJson(enrollmentService.findTournamentIdsByParticipant(user.userId()));
    }

    /**
     * Registra lo stato avviso lettura.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object markNoticeRead(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        pairingService.markRead(user.userId(), ControllerSupport.pathLong(req, "matchId"));
        res.status(204);
        return "";
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object search(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.search(ControllerSupport.page(req)));
    }

    /**
     * Crea il torneo tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        CreateTournamentRequestDTO body = JsonSupport.fromJson(req.body(), CreateTournamentRequestDTO.class);
        Tournament creato = tournamentService.create(body.nome(), body.gameTypeId(), body.modalita(), body.formula(),
                body.puntiVittoria(), body.puntiPareggio(), body.puntiSconfitta(), body.dataInizio(), body.dataFine());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.findByIdDto(creato.id()));
    }

    /**
     * Cerca tornei per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Aggiorna il torneo tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object update(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        UpdateTournamentRequestDTO body = JsonSupport.fromJson(req.body(), UpdateTournamentRequestDTO.class);
        tournamentService.update(id, body.nome(), body.puntiVittoria(), body.puntiPareggio(), body.puntiSconfitta());
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.findByIdDto(id));
    }

    /**
     * Elimina il torneo tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object delete(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        tournamentService.delete(ControllerSupport.pathLong(req, "id"));
        res.status(204);
        return "";
    }

    /**
     * Associa un locale al torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object addLocale(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        AddLocaleRequestDTO body = JsonSupport.fromJson(req.body(), AddLocaleRequestDTO.class);
        if (body.localeId() == null) {
            throw new it.uniupo.pissir.darts.common.exception.ValidationException("Campo obbligatorio mancante: localeId");
        }
        tournamentService.addLocale(id, body.localeId());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.findByIdDto(id));
    }

    /**
     * Rimuove l'associazione fra locale e torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object removeLocale(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        long localeId = ControllerSupport.pathLong(req, "localeId");
        tournamentService.removeLocale(id, localeId);
        res.status(204);
        return "";
    }

    /**
     * Cerca tornei iscrizioni.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findEnrollments(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(enrollmentService.findByTournament(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Iscrive il partecipante al torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object enroll(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        EnrollRequestDTO body = JsonSupport.fromJson(req.body(), EnrollRequestDTO.class);
        requireEnrollmentAllowed(user, id, body.participantId());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(enrollmentService.enroll(id, body.participantId()));
    }

    /**
     * Registra il ritiro del partecipante dal torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object withdraw(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        long participantId = ControllerSupport.pathLong(req, "pid");
        requireEnrollmentAllowed(user, id, participantId);
        enrollmentService.withdraw(id, participantId);
        res.status(204);
        return "";
    }

    /**
     * Verifica che il giocatore possa iscriversi al torneo.
     *
     * @param user utente da utilizzare
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @throws ForbiddenException se viene rilevato l'errore «Un giocatore può iscrivere solo se stesso, e solo a un torneo INDIVIDUALE»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private void requireEnrollmentAllowed(AuthenticatedUser user, long tournamentId, Long participantId) {
        if (user.hasAnyRole(Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name())) {
            return;
        }
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        Tournament tournament = tournamentService.requireTournament(tournamentId);
        boolean sePropria = tournament.modalita() == ModalitaTorneo.INDIVIDUALE
                && participantId != null && participantId == user.userId();
        if (!sePropria) {
            throw new ForbiddenException("NON_AUTORIZZATO",
                    "Un giocatore può iscrivere solo se stesso, e solo a un torneo INDIVIDUALE");
        }
    }

    /**
     * Associa una partita al torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object addMatch(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        AddMatchRequestDTO body = JsonSupport.fromJson(req.body(), AddMatchRequestDTO.class);
        if (body.matchId() == null) {
            throw new it.uniupo.pissir.darts.common.exception.ValidationException("Campo obbligatorio mancante: matchId");
        }
        tournamentService.addMatch(id, body.matchId());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(Map.of("tournamentId", id, "matchId", body.matchId()));
    }

    /**
     * Rimuove una partita dal torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object removeMatch(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        long matchId = ControllerSupport.pathLong(req, "matchId");
        tournamentService.removeMatch(id, matchId);
        res.status(204);
        return "";
    }

    /**
     * Genera il calendario delle partite del torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object generaCalendario(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        boolean force = ControllerSupport.queryBoolean(req, "force");
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(Map.of("matchIds", scheduleGenerator.generaCalendario(id, force)));
    }

    /**
     * Ferma l'attività e chiude le risorse del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object chiudi(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name(), Role.GAME_ADMIN.name());
        long id = ControllerSupport.pathLong(req, "id");
        tournamentService.chiudi(id);
        res.type("application/json");
        return JsonSupport.toJson(tournamentService.findByIdDto(id));
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
