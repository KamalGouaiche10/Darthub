package it.uniupo.pissir.darts.common.exception;

import it.uniupo.pissir.darts.common.json.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Associa le eccezioni applicative alle risposte HTTP. 
 */
public final class SparkExceptionHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(SparkExceptionHandlers.class);

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private SparkExceptionHandlers() {
    }

    /**
     * Registra il spark exception handlers applicando i controlli previsti. 
     */
    public static void register() {
        Spark.exception(DomainException.class, (e, req, res) -> {
            res.status(e.status());
            res.type("application/json");
            res.body(JsonSupport.toJson(ErrorResponse.of(e.status(), e.codice(), e.getMessage(), req.pathInfo())));
        });

        Spark.exception(Exception.class, (e, req, res) -> {
            LOG.error("Errore non gestito su {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.type("application/json");
            res.body(JsonSupport.toJson(ErrorResponse.of(500, "ERRORE_INTERNO", "Si è verificato un errore interno", req.pathInfo())));
        });
    }
}
