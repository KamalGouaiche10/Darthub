package it.uniupo.pissir.darts.simulator.web;

import it.uniupo.pissir.darts.common.exception.SparkExceptionHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

/**
 * Avvia il pannello HTTP del sensore simulato. 
 */
public final class SimulatorWebServer {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorWebServer.class);

    private final SimulatorController controller;
    private final int porta;

    /**
     * Inizializza {@code SimulatorWebServer} con i dati e le dipendenze indicati.
     *
     * @param controller controller da utilizzare
     * @param porta porta da utilizzare
     * @see it.uniupo.pissir.darts.simulator.web.SimulatorController
     *  
     */
    public SimulatorWebServer(SimulatorController controller, int porta) {
        this.controller = controller;
        this.porta = porta;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        Spark.port(porta);
        Spark.staticFiles.location("/simulatore-web");
        SparkExceptionHandlers.register();
        controller.register();
        Spark.init();
        Spark.awaitInitialization();
        LOG.info("Server web del simulatore avviato sulla porta {}", porta);
    }

    /**
     * Ferma l'attività del componente. 
     */
    public void stop() {
        Spark.stop();
        Spark.awaitStop();
    }
}
