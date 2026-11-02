package it.uniupo.pissir.darts.tournament.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dto.StandingDTO;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.service.UserClient;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET /tornei/{id/classifica}.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.StandingDao
 *  
 */
public final class StandingsController {

    private final StandingDao standingDao;
    private final TournamentService tournamentService;
    private final UserClient userClient;
    private final MatchServiceClient matchServiceClient;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code StandingsController} con i dati e le dipendenze indicati.
     *
     * @param standingDao componente {@link it.uniupo.pissir.darts.tournament.dao.StandingDao} utilizzato dall'operazione
     * @param tournamentService componente {@link it.uniupo.pissir.darts.tournament.service.TournamentService} utilizzato dall'operazione
     * @param userClient componente {@link it.uniupo.pissir.darts.tournament.service.UserClient} utilizzato dall'operazione
     * @param matchServiceClient componente {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.tournament.dao.StandingDao
     *  
     */
    public StandingsController(StandingDao standingDao, TournamentService tournamentService,
                               UserClient userClient, MatchServiceClient matchServiceClient,
                               JwtTokenService jwtTokenService) {
        this.standingDao = standingDao;
        this.tournamentService = tournamentService;
        this.userClient = userClient;
        this.matchServiceClient = matchServiceClient;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/tornei/:id/classifica", this::classifica);
    }

    /**
     * Legge la classifica secondo i criteri indicati.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object classifica(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        long tournamentId = ControllerSupport.pathLong(req, "id");
        ModalitaTorneo modalita = tournamentService.requireTournament(tournamentId).modalita();
        res.type("application/json");
        return JsonSupport.toJson(standingDao.findByTournament(tournamentId).stream()
                .map(s -> new StandingDTO(s.participantId(),
                        modalita == ModalitaTorneo.INDIVIDUALE
                                ? userClient.displayName(s.participantId())
                                : matchServiceClient.teamName(s.participantId()),
                        s.punti(), s.partiteGiocate(), s.vittorie(), s.posizione()))
                .toList());
    }
}
