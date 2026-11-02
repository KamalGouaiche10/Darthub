package it.uniupo.pissir.darts.statistics.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao;
import it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao;
import it.uniupo.pissir.darts.statistics.dto.PlayerStatisticsDTO;
import it.uniupo.pissir.darts.statistics.dto.TeamStatisticsDTO;
import it.uniupo.pissir.darts.statistics.model.PlayerStatistics;
import it.uniupo.pissir.darts.statistics.model.Role;
import it.uniupo.pissir.darts.statistics.model.TeamStatistics;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * Espone le operazioni HTTP relative a giocatori.
 *
 * @see it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao
 *  
 */
public final class PlayerStatisticsController {

    private final PlayerStatisticsDao playerStatisticsDao;
    private final TeamStatisticsDao teamStatisticsDao;
    private final PlayerVisibilityDao playerVisibilityDao;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code PlayerStatisticsController} con i dati e le dipendenze indicati.
     *
     * @param playerStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao} utilizzato dall'operazione
     * @param teamStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao} utilizzato dall'operazione
     * @param playerVisibilityDao componente {@link it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao
     *  
     */
    public PlayerStatisticsController(PlayerStatisticsDao playerStatisticsDao, TeamStatisticsDao teamStatisticsDao,
                                       PlayerVisibilityDao playerVisibilityDao, JwtTokenService jwtTokenService) {
        this.playerStatisticsDao = playerStatisticsDao;
        this.teamStatisticsDao = teamStatisticsDao;
        this.playerVisibilityDao = playerVisibilityDao;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/statistiche/giocatori/:id", this::findPlayer);
        Spark.get("/statistiche/squadre/:id", this::findTeam);
        Spark.get("/statistiche/classifica", this::classifica);
    }

    /**
     * Cerca giocatori giocatore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ForbiddenException se viene rilevato l'errore «Puoi vedere solo le tue statistiche»
     * @throws NotFoundException se i dati o lo stato del componente non consentono l'operazione  
     *  
     */
    private Object findPlayer(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        long playerId = ControllerSupport.pathLong(req, "id");
        if (playerId != user.userId() && !user.hasAnyRole(Role.PLATFORM_ADMIN.name())) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Puoi vedere solo le tue statistiche");
        }

        if (!playerVisibilityDao.isVisibile(playerId)) {
            throw new NotFoundException("Nessuna statistica per il giocatore " + playerId);
        }
        PlayerStatistics statistiche = playerStatisticsDao.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Nessuna statistica per il giocatore " + playerId));
        res.type("application/json");
        return JsonSupport.toJson(toDto(statistiche));
    }

    /**
     * Cerca giocatori squadra.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws NotFoundException se viene rilevato l'errore «Nessuna statistica per la squadra»  
     *  
     */
    private Object findTeam(Request req, Response res) {
        authenticate(req);
        long teamId = ControllerSupport.pathLong(req, "id");
        TeamStatistics statistiche = teamStatisticsDao.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Nessuna statistica per la squadra " + teamId));
        res.type("application/json");
        return JsonSupport.toJson(toDto(statistiche));
    }

    /**
     * Legge la classifica secondo i criteri indicati.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «ordinaPer deve essere percentualeVittorie o puntiTotali»  
     *  
     */
    private Object classifica(Request req, Response res) {
        authenticate(req);
        String ordinaPer = req.queryParams("ordinaPer");
        if (ordinaPer != null && !ordinaPer.equals("percentualeVittorie") && !ordinaPer.equals("puntiTotali")) {
            throw new ValidationException("ordinaPer deve essere percentualeVittorie o puntiTotali");
        }
        res.type("application/json");
        return JsonSupport.toJson(playerStatisticsDao.classifica(ControllerSupport.page(req), ordinaPer).stream()
                .map(this::toDto).toList());
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param s dato {@link it.uniupo.pissir.darts.statistics.model.PlayerStatistics} da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.dto.PlayerStatisticsDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.statistics.dto.PlayerStatisticsDTO
     *  
     */
    private PlayerStatisticsDTO toDto(PlayerStatistics s) {
        return new PlayerStatisticsDTO(s.playerId(), s.partiteGiocate(), s.vittorie(), s.percentualeVittorie(),
                s.puntiTotali(), s.punteggioMedio(), s.migliorTiro(), s.updatedAt().toString());
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param s dato {@link it.uniupo.pissir.darts.statistics.model.TeamStatistics} da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.dto.TeamStatisticsDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.statistics.dto.TeamStatisticsDTO
     *  
     */
    private TeamStatisticsDTO toDto(TeamStatistics s) {
        return new TeamStatisticsDTO(s.teamId(), s.partiteGiocate(), s.vittorie(), s.percentualeVittorie(),
                s.puntiTotali(), s.punteggioMedio(), s.updatedAt().toString());
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
