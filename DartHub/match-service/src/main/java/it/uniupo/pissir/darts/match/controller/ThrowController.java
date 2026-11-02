package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.dto.CreateMatchEventRequestDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.MatchService;
import it.uniupo.pissir.darts.match.service.ThrowIngestionService;
import it.uniupo.pissir.darts.match.service.TournamentAccessClient;
import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.Instant;
import java.util.Map;

/**
 * GET /partite/{id/tiri} (PLAYER), POST /partite/{id/tiri} (EDGE_DEVICE, fallback REST all'MQTT — 4, rotte 37-38).
 *
 * @see it.uniupo.pissir.darts.match.service.MatchService
 *  
 */
public final class ThrowController {

    private final MatchService matchService;
    private final ThrowIngestionService throwIngestionService;
    private final JwtTokenService jwtTokenService;
    private final TournamentAccessClient tournamentAccess;

    /**
     * Inizializza {@code ThrowController} con i dati e le dipendenze indicati.
     *
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @param throwIngestionService componente {@link it.uniupo.pissir.darts.match.service.ThrowIngestionService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.MatchService
     *  
     */
    public ThrowController(MatchService matchService, ThrowIngestionService throwIngestionService,
                            JwtTokenService jwtTokenService) {
        this(matchService, throwIngestionService, jwtTokenService, null);
    }

    /**
     * Inizializza {@code ThrowController} con i dati e le dipendenze indicati.
     *
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @param throwIngestionService componente {@link it.uniupo.pissir.darts.match.service.ThrowIngestionService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param tournamentAccess torneo accesso da utilizzare
     * @see it.uniupo.pissir.darts.match.service.MatchService
     *  
     */
    public ThrowController(MatchService matchService, ThrowIngestionService throwIngestionService,
                            JwtTokenService jwtTokenService, TournamentAccessClient tournamentAccess) {
        this.matchService = matchService;
        this.throwIngestionService = throwIngestionService;
        this.jwtTokenService = jwtTokenService;
        this.tournamentAccess = tournamentAccess;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/partite/:id/tiri", this::findByMatch);
        Spark.post("/partite/:id/tiri", this::create);
        Spark.get("/partite/:id/bersaglio/punteggio", this::virtualScore);
        Spark.post("/partite/:id/bersaglio/tiri", this::virtualThrow);
    }

    /**
     * Raccoglie i dati di virtual throw.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     *  
     */
    private record VirtualThrow(String settore, String moltiplicatore) { }

    /**
     * Verifica il requisito virtual giocatore prima di eseguire l'operazione.
     *
     * @param req richiesta HTTP ricevuta
     * @return il valore numerico calcolato o letto
     * @throws ForbiddenException se viene rilevato l'errore «Bersaglio virtuale non configurato»  
     *  
     */
    private long requireVirtualPlayer(Request req) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long matchId = ControllerSupport.pathLong(req, "id");
        if (tournamentAccess == null) throw new ForbiddenException("Bersaglio virtuale non configurato");
        tournamentAccess.requireAccess(matchId, req.headers("Authorization"));
        return matchId;
    }

    /**
     * Legge il punteggio del bersaglio virtuale condiviso.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object virtualScore(Request req, Response res) {
        long matchId = requireVirtualPlayer(req);
        res.type("application/json");
        return JsonSupport.toJson(matchService.punteggio(matchId));
    }

    /**
     * Applica il tiro ricevuto dal bersaglio virtuale condiviso.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «settore e moltiplicatore obbligatori»
     * @throws ForbiddenException se viene rilevato l'errore «Non sei un partecipante della partita»  
     *  
     */
    private Object virtualThrow(Request req, Response res) {
        long matchId = requireVirtualPlayer(req);
        if (matchService.isSensorStarted(matchId))
            throw new it.uniupo.pissir.darts.common.exception.ConflictException(
                    "USA_SIMULATORE", "Questa partita usa il simulatore del sensore assegnato");
        long playerId = authenticate(req).userId();
        var participant = matchService.roster(matchId).stream()
                .filter(p -> p.playerId() != null && p.playerId() == playerId).findFirst()
                .orElseThrow(() -> new ForbiddenException("Non sei un partecipante della partita"));
        VirtualThrow body = JsonSupport.fromJson(req.body(), VirtualThrow.class);
        if (body == null || body.settore() == null || body.moltiplicatore() == null)
            throw new ValidationException("settore e moltiplicatore obbligatori");
        GameEvent evento = GameEvent.grezzo("TIRO", Map.of("settore", body.settore(),
                "moltiplicatore", body.moltiplicatore()), Instant.now())
                .conMatchEPartecipante(matchId, participant.participantId());
        boolean accepted = throwIngestionService.ingerisci(evento);
        if (!accepted) throw new it.uniupo.pissir.darts.common.exception.ConflictException(
                "TIRO_NON_AMMESSO", "Partita chiusa o turno dell'altro giocatore");
        res.status(202); res.type("application/json");
        return JsonSupport.toJson(Map.of("accettato", true));
    }

    /**
     * Cerca tiri per partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ForbiddenException se viene rilevato l'errore «Verifica torneo non disponibile»  
     *  
     */
    private Object findByMatch(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long matchId = ControllerSupport.pathLong(req, "id");
        Match match = matchService.requireMatch(matchId);
        if (match.localUuid() != null && match.localUuid().startsWith("tournament:")) {
            if (tournamentAccess == null) throw new ForbiddenException("Verifica torneo non disponibile");
            tournamentAccess.requireParticipant(matchId, req.headers("Authorization"));
        }
        res.type("application/json");
        return JsonSupport.toJson(matchService.listaTiri(matchId, ControllerSupport.page(req)));
    }

    /**
     * Crea il tiro tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «participantId e tipo sono obbligatori»  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.EDGE_DEVICE.name());
        long matchId = ControllerSupport.pathLong(req, "id");
        Match match = matchService.requireMatch(matchId);
        RequestAuthentication.requireOwnDartboard(user, match.dartboardId());

        CreateMatchEventRequestDTO body = JsonSupport.fromJson(req.body(), CreateMatchEventRequestDTO.class);
        if (body.participantId() == null || body.tipo() == null || body.tipo().isBlank()) {
            throw new ValidationException("participantId e tipo sono obbligatori");
        }
        Instant timestampSensore = body.timestampSensore() != null ? Instant.parse(body.timestampSensore()) : Instant.now();
        Map<String, Object> payload = body.payload() != null ? body.payload() : Map.of();

        GameEvent evento = GameEvent.grezzo(body.tipo(), payload, timestampSensore)
                .conMatchEPartecipante(matchId, body.participantId());
        boolean accettato = throwIngestionService.ingerisci(evento);
        res.status(accettato ? 202 : 200);
        res.type("application/json");
        return JsonSupport.toJson(Map.of("accettato", accettato));
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
