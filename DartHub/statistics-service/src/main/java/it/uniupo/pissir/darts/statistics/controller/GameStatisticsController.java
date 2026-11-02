package it.uniupo.pissir.darts.statistics.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao;
import it.uniupo.pissir.darts.statistics.dto.GameStatisticsDTO;
import it.uniupo.pissir.darts.statistics.model.GameStatistics;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET /statistiche/giochi/{id}.
 *
 * @see it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao
 *  
 */
public final class GameStatisticsController {

    private final GameStatisticsDao gameStatisticsDao;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code GameStatisticsController} con i dati e le dipendenze indicati.
     *
     * @param gameStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao
     *  
     */
    public GameStatisticsController(GameStatisticsDao gameStatisticsDao, JwtTokenService jwtTokenService) {
        this.gameStatisticsDao = gameStatisticsDao;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/statistiche/giochi/:id", this::findByGameType);
    }

    /**
     * Cerca gioco statistics per gioco tipo.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws NotFoundException se viene rilevato l'errore «Nessuna statistica per il tipo di gioco»  
     *  
     */
    private Object findByGameType(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        long gameTypeId = ControllerSupport.pathLong(req, "id");
        GameStatistics statistiche = gameStatisticsDao.findById(gameTypeId)
                .orElseThrow(() -> new NotFoundException("Nessuna statistica per il tipo di gioco " + gameTypeId));
        res.type("application/json");
        return JsonSupport.toJson(new GameStatisticsDTO(statistiche.gameTypeId(), statistiche.partiteTotali(),
                statistiche.mediaGiocatoriPerPartita()));
    }
}
