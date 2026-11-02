package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.dto.CreateMatchDTO;
import it.uniupo.pissir.darts.match.dto.MatchDTO;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.MatchService;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * POST/GET /partite, GET /partite/{id}, GET /partite/{id/punteggio}, POST /partite/{id/avvia\|fine}, DELETE /partite/{id}.
 *
 * @see it.uniupo.pissir.darts.match.service.MatchService
 *  
 */
public final class MatchController {

    private final MatchService matchService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code MatchController} con i dati e le dipendenze indicati.
     *
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.MatchService
     *  
     */
    public MatchController(MatchService matchService, JwtTokenService jwtTokenService) {
        this.matchService = matchService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/partite", this::create);
        Spark.get("/partite", this::search);
        Spark.get("/partite/:id", this::findById);
        Spark.get("/partite/:id/punteggio", this::punteggio);
        Spark.post("/partite/:id/avvia", this::avvia);
        Spark.post("/partite/:id/avvia-torneo", this::avviaTorneo);
        Spark.post("/partite/:id/assenza", this::assenza);
        Spark.get("/partite/:id/esito-torneo", this::esitoTorneo);
        Spark.post("/partite/:id/fine", this::fine);
        Spark.post("/partite/:id/ripubblica-esito", this::ripubblicaEsito);
        Spark.delete("/partite/:id/bozza", this::annullaBozza);
        Spark.delete("/partite/:id", this::annulla);
    }

    /**
     * Crea la partita tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object create(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name(), "SERVICE");
        CreateMatchDTO body = JsonSupport.fromJson(req.body(), CreateMatchDTO.class);
        if (body.idempotencyKey() != null) RequestAuthentication.requireAnyRole(user, "SERVICE");
        ModalitaPartita modalita = parseModalita(body.modalita());
        MatchDTO creata = matchService.create(require(body.dartboardId(), "dartboardId"), require(body.localeId(), "localeId"),
                require(body.gameTypeId(), "gameTypeId"), require(body.scoringRuleId(), "scoringRuleId"), modalita,
                user.userId(), body.idempotencyKey());
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(creata);
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
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name(), Role.LOCALE_ADMIN.name(),
                Role.GAME_ADMIN.name(), Role.PLATFORM_ADMIN.name(), "SERVICE");
        Long playerId = ControllerSupport.queryLongOrNull(req, "playerId");
        Long localeId = ControllerSupport.queryLongOrNull(req, "localeId");
        MatchStatus stato = parseStatoOrNull(req.queryParams("stato"));
        res.type("application/json");
        return JsonSupport.toJson(matchService.search(playerId, localeId, stato,
                ControllerSupport.queryInstantOrNull(req, "dal"), ControllerSupport.queryInstantOrNull(req, "al"),
                ControllerSupport.page(req)));
    }

    /**
     * Cerca partite per identificativo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object findById(Request req, Response res) {
        authenticate(req);
        res.type("application/json");
        return JsonSupport.toJson(matchService.findByIdDto(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Legge il punteggio corrente della partita o del giocatore richiesto.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object punteggio(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long id = ControllerSupport.pathLong(req, "id");
        var match = matchService.requireMatch(id);
        if (match.localUuid() != null && match.localUuid().startsWith("tournament:"))
            RequestAuthentication.requireAnyRole(user, "SERVICE");
        res.type("application/json");
        return JsonSupport.toJson(matchService.punteggio(id));
    }

    /**
     * Avvia l'elaborazione del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object avvia(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long id = ControllerSupport.pathLong(req, "id");
        requireCreator(user, id);
        res.type("application/json");
        return JsonSupport.toJson(matchService.avvia(id));
    }

    /**
     * Avvia una partita appartenente a un torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object avviaTorneo(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, "SERVICE");
        long id = ControllerSupport.pathLong(req, "id");
        var match = matchService.requireMatch(id);
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:"))
            throw new it.uniupo.pissir.darts.common.exception.ForbiddenException("Solo partite di torneo");
        res.type("application/json");
        return JsonSupport.toJson(matchService.avviaTorneo(id));
    }

    /**
     * Raccoglie i dati della richiesta relativa a absence.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param winnerPlayerId identificativo del giocatore vincitore
     *  
     */
    private record AbsenceRequest(Long winnerPlayerId) { }

    /**
     * Gestisce la richiesta di conclusione della partita per assenza.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object assenza(Request req, Response res) {
        RequestAuthentication.requireAnyRole(authenticate(req), "SERVICE");
        AbsenceRequest body = JsonSupport.fromJson(req.body(), AbsenceRequest.class);
        res.type("application/json");
        return JsonSupport.toJson(matchService.resolveAbsence(ControllerSupport.pathLong(req, "id"),
                body == null ? null : body.winnerPlayerId()));
    }

    /**
     * Legge l'esito della partita nel contesto del torneo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object esitoTorneo(Request req, Response res) {
        RequestAuthentication.requireAnyRole(authenticate(req), "SERVICE");
        res.type("application/json");
        return JsonSupport.toJson(matchService.completedEvent(ControllerSupport.pathLong(req, "id")));
    }

    /**
     * Conclude la partita e libera il bersaglio.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ForbiddenException se viene rilevato l'errore «Le partite di torneo seguono la gestione del torneo»  
     *  
     */
    private Object fine(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name(), Role.LOCALE_ADMIN.name(),
                Role.GAME_ADMIN.name(), Role.PLATFORM_ADMIN.name(), "SERVICE");
        long id = ControllerSupport.pathLong(req, "id");
        var match = matchService.requireMatch(id);
        if (match.localUuid() != null && match.localUuid().startsWith("tournament:")) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Le partite di torneo seguono la gestione del torneo");
        }
        if (user.hasAnyRole(Role.PLAYER.name())) {
            requireCreator(user, id);
        } else if (user.hasAnyRole(Role.LOCALE_ADMIN.name())
                && !matchService.isLocaleOwner(match.localeId(), user.userId())) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Il locale non appartiene a questo gestore");
        }
        res.type("application/json");
        return JsonSupport.toJson(matchService.fine(id));
    }

    /**
     * Annulla la partita dopo averne verificato lo stato.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object annulla(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long id = ControllerSupport.pathLong(req, "id");
        requireCreator(user, id);
        res.type("application/json");
        return JsonSupport.toJson(matchService.annulla(id));
    }

    /**
     * Ripubblica esito per la partita.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object ripubblicaEsito(Request req, Response res) {
        RequestAuthentication.requireAnyRole(authenticate(req), "SERVICE");
        matchService.ripubblicaEsito(ControllerSupport.pathLong(req, "id"));
        res.status(204);
        return "";
    }

    /**
     * Annulla una partita non ancora avviata.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object annullaBozza(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.PLAYER.name());
        long id = ControllerSupport.pathLong(req, "id");
        requireCreator(user, id);
        res.type("application/json");
        return JsonSupport.toJson(matchService.annullaBozza(id));
    }

    /**
     * Verifica il requisito creator prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param matchId identificativo della partita
     * @throws ForbiddenException se viene rilevato l'errore «Solo il creatore può modificare la partita»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private void requireCreator(AuthenticatedUser user, long matchId) {
        if (matchService.requireMatch(matchId).createdBy() != user.userId()) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Solo il creatore può modificare la partita");
        }
    }

    /**
     * Legge e converte modalita dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.ModalitaPartita} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.ModalitaPartita
     *  
     */
    private ModalitaPartita parseModalita(String value) {
        try {
            return ModalitaPartita.valueOf(value);
        } catch (RuntimeException e) {
            throw new it.uniupo.pissir.darts.common.exception.ValidationException("Modalità non valida: " + value);
        }
    }

    /**
     * Legge e converte stato o assente dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchStatus
     *  
     */
    private MatchStatus parseStatoOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MatchStatus.valueOf(value);
        } catch (RuntimeException e) {
            throw new it.uniupo.pissir.darts.common.exception.ValidationException("Stato non valido: " + value);
        }
    }

    /**
     * Verifica il requisito  prima di eseguire l'operazione.
     *
     * @param value valore numerico da elaborare
     * @param campo nome del campo da leggere
     * @return il valore numerico calcolato o letto
     *  
     */
    private long require(Long value, String campo) {
        if (value == null) {
            throw new it.uniupo.pissir.darts.common.exception.ValidationException("Campo obbligatorio mancante: " + campo);
        }
        return value;
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
