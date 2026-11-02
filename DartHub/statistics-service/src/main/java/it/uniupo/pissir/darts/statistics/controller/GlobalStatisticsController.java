package it.uniupo.pissir.darts.statistics.controller;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao;
import it.uniupo.pissir.darts.statistics.dto.GlobalStatisticsDTO;
import it.uniupo.pissir.darts.statistics.model.GlobalStatistics;
import it.uniupo.pissir.darts.statistics.model.Role;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.Instant;

/**
 * GET /statistiche/globali (PLATFORM_ADMIN, 6).
 *
 * @see it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao
 *  
 */
public final class GlobalStatisticsController {

    private final GlobalStatisticsDao globalStatisticsDao;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code GlobalStatisticsController} con i dati e le dipendenze indicati.
     *
     * @param globalStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao
     *  
     */
    public GlobalStatisticsController(GlobalStatisticsDao globalStatisticsDao, JwtTokenService jwtTokenService) {
        this.globalStatisticsDao = globalStatisticsDao;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/statistiche/globali", this::find);
    }

    /**
     * Cerca il global statistics tramite la richiesta HTTP.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object find(Request req, Response res) {
        AuthenticatedUser user = RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        RequestAuthentication.requireAnyRole(user, Role.PLATFORM_ADMIN.name());
        GlobalStatistics statistiche = globalStatisticsDao.find()
                .orElse(new GlobalStatistics(0, 0, 0, 0, Instant.now()));
        res.type("application/json");
        return JsonSupport.toJson(new GlobalStatisticsDTO(statistiche.utentiTotali(), statistiche.localiTotali(),
                statistiche.partiteTotali(), statistiche.torneiTotali(), statistiche.updatedAt().toString()));
    }
}
