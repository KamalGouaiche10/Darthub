package it.uniupo.pissir.darts.statistics.controller;

import it.uniupo.pissir.darts.common.http.ControllerSupport;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao;
import it.uniupo.pissir.darts.statistics.dto.LocaleStatisticsDTO;
import it.uniupo.pissir.darts.statistics.model.LocaleStatistics;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * GET /statistiche/locali/{id}, GET /statistiche/bersagli/{id}.
 *
 * @see it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao
 *  
 */
public final class LocaleStatisticsController {

    private final LocaleStatisticsDao localeStatisticsDao;
    private final LocaleVisibilityDao localeVisibilityDao;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code LocaleStatisticsController} con i dati e le dipendenze indicati.
     *
     * @param localeStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao} utilizzato dall'operazione
     * @param localeVisibilityDao componente {@link it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao
     *  
     */
    public LocaleStatisticsController(LocaleStatisticsDao localeStatisticsDao, LocaleVisibilityDao localeVisibilityDao,
                                       JwtTokenService jwtTokenService) {
        this.localeStatisticsDao = localeStatisticsDao;
        this.localeVisibilityDao = localeVisibilityDao;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/statistiche/locali/:id", this::findLocale);
        Spark.get("/statistiche/bersagli/:id", this::findDartboard);
    }

    /**
     * Cerca locali locale.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws NotFoundException se viene rilevato l'errore «Nessuna statistica per il locale»  
     *  
     */
    private Object findLocale(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        long localeId = ControllerSupport.pathLong(req, "id");
        requireVisibile(localeId);
        LocaleStatistics statistiche = localeStatisticsDao.findByLocale(localeId)
                .orElseThrow(() -> new NotFoundException("Nessuna statistica per il locale " + localeId));
        res.type("application/json");
        return JsonSupport.toJson(toDto(statistiche, localeId));
    }

    /**
     * Cerca locali bersaglio.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws NotFoundException se viene rilevato l'errore «Nessuna statistica per il bersaglio»  
     *  
     */
    private Object findDartboard(Request req, Response res) {
        RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
        long dartboardId = ControllerSupport.pathLong(req, "id");
        LocaleStatistics statistiche = localeStatisticsDao.findByDartboard(dartboardId)
                .orElseThrow(() -> new NotFoundException("Nessuna statistica per il bersaglio " + dartboardId));
        requireVisibile(statistiche.localeId());
        res.type("application/json");
        return JsonSupport.toJson(toDto(statistiche, statistiche.localeId()));
    }

    /**
     * Verifica che la risorsa sia visibile all'utente.
     *
     * @param localeId identificativo del locale
     * @throws NotFoundException se i dati o lo stato del componente non consentono l'operazione
     *  
     */
    private void requireVisibile(long localeId) {
        if (!localeVisibilityDao.isVisibile(localeId)) {
            throw new NotFoundException("Nessuna statistica per il locale " + localeId);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param s dato {@link it.uniupo.pissir.darts.statistics.model.LocaleStatistics} da elaborare
     * @param localeId identificativo del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.statistics.dto.LocaleStatisticsDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.statistics.dto.LocaleStatisticsDTO
     *  
     */
    private LocaleStatisticsDTO toDto(LocaleStatistics s, long localeId) {
        return new LocaleStatisticsDTO(localeId, s.dartboardId(), s.numeroPartite(), s.giocatoriDistinti(),
                s.ultimoUtilizzo() == null ? null : s.ultimoUtilizzo().toString());
    }
}
