package it.uniupo.pissir.darts.edge.web;

import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Avvia il server HTTP del monitor locale dell'edge. 
 */
public final class LocalDisplayServer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDisplayServer.class);

    private final LocalScoreController scoreController;
    private final int porta;

    /**
     * Inizializza {@code LocalDisplayServer} con i dati e le dipendenze indicati.
     *
     * @param scoreController componente {@link it.uniupo.pissir.darts.edge.web.LocalScoreController} utilizzato dall'operazione
     * @param porta porta da utilizzare
     * @see it.uniupo.pissir.darts.edge.web.LocalScoreController
     *  
     */
    public LocalDisplayServer(LocalScoreController scoreController, int porta) {
        this.scoreController = scoreController;
        this.porta = porta;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        Spark.port(porta);
        Spark.staticFiles.location("/display");
        SparkExceptionHandlers.register();
        scoreController.register();
        Spark.init();
        Spark.awaitInitialization();
        LOG.info("Monitor locale avviato sulla porta {}", porta);
    }

}
