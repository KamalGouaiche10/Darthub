package it.uniupo.pissir.darts.simulator.web;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.simulator.generator.DartSector;
import it.uniupo.pissir.darts.simulator.generator.GeneratedThrow;
import it.uniupo.pissir.darts.simulator.generator.Multiplier;
import it.uniupo.pissir.darts.simulator.generator.Scenario;
import it.uniupo.pissir.darts.simulator.service.SimulatorEngine;
import org.eclipse.paho.client.mqttv3.MqttException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Locale;

/**
 * Espone stato e comandi del sensore simulato.
 *
 * @see it.uniupo.pissir.darts.simulator.service.SimulatorEngine
 *  
 */
public final class SimulatorController {

    private final SimulatorEngine engine;

    /**
     * Inizializza {@code SimulatorController} con i dati e le dipendenze indicati.
     *
     * @param engine engine da utilizzare
     * @see it.uniupo.pissir.darts.simulator.service.SimulatorEngine
     *  
     */
    public SimulatorController(SimulatorEngine engine) {
        this.engine = engine;
    }

    /**
     * Raccoglie i dati della richiesta relativa a velocita.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param intervalloMs intervallo fra i tiri in millisecondi
     *  
     */
    public record VelocitaRequest(Long intervalloMs) {
    }

    /**
     * Raccoglie i dati della richiesta relativa a scenario.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param scenario scenario di generazione dei tiri
     *  
     */
    public record ScenarioRequest(String scenario) {
    }

    /**
     * Raccoglie i dati della richiesta relativa a tiro.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     *  
     */
    public record TiroRequest(String settore, String moltiplicatore) {
    }

    /**
     * Raccoglie i dati della richiesta relativa a disconnetti.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param secondi intervallo in secondi
     *  
     */
    public record DisconnettiRequest(Long secondi) {
    }

    /**
     * Raccoglie i dati della risposta relativa a tiro.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @see it.uniupo.pissir.darts.simulator.generator.DartSector
     *  
     */
    public record TiroResponse(DartSector settore, Multiplier moltiplicatore, int punti) {
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/simulatore/stato", this::stato);
        Spark.post("/simulatore/avvia", this::avvia);
        Spark.post("/simulatore/ferma", this::ferma);
        Spark.post("/simulatore/velocita", this::velocita);
        Spark.post("/simulatore/scenario", this::scenario);
        Spark.post("/simulatore/tiro", this::tiro);
        Spark.post("/simulatore/disconnetti", this::disconnetti);
    }

    /**
     * Restituisce un'istantanea dello stato del simulatore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object stato(Request req, Response res) {
        res.type("application/json");
        return JsonSupport.toJson(engine.statoCorrente());
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
        requireExpectedMatch(req);
        engine.avvia();
        res.status(204);
        return "";
    }

    /**
     * Ferma l'attività del componente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object ferma(Request req, Response res) {
        requireExpectedMatch(req);
        engine.ferma();
        res.status(204);
        return "";
    }

    /**
     * Aggiorna l'intervallo fra i tiri del simulatore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «intervalloMs è obbligatorio»  
     *  
     */
    private Object velocita(Request req, Response res) {
        requireExpectedMatch(req);
        VelocitaRequest body = JsonSupport.fromJson(req.body(), VelocitaRequest.class);
        if (body.intervalloMs() == null) {
            throw new ValidationException("intervalloMs è obbligatorio");
        }

        engine.cambiaVelocita(body.intervalloMs());
        res.status(204);
        return "";
    }

    /**
     * Seleziona lo scenario di generazione dei tiri.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object scenario(Request req, Response res) {
        requireExpectedMatch(req);
        ScenarioRequest body = JsonSupport.fromJson(req.body(), ScenarioRequest.class);
        Scenario nuovo;
        try {
            nuovo = Scenario.valueOf(body.scenario().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Scenario non valido: " + body.scenario());
        }
        engine.cambiaScenario(nuovo);
        res.status(204);
        return "";
    }

    /**
     * Applica il tiro richiesto al sensore simulato.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object tiro(Request req, Response res) {
        requireExpectedMatch(req);
        TiroRequest body = JsonSupport.fromJson(req.body(), TiroRequest.class);
        DartSector settore;
        Multiplier moltiplicatore;
        try {
            settore = DartSector.valueOf(body.settore().toUpperCase(Locale.ROOT));
            moltiplicatore = Multiplier.valueOf(body.moltiplicatore().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Settore o moltiplicatore non valido");
        }
        GeneratedThrow tiro = engine.tiroManuale(settore, moltiplicatore);
        res.type("application/json");
        return JsonSupport.toJson(new TiroResponse(tiro.settore(), tiro.moltiplicatore(), tiro.punti()));
    }

    /**
     * Verifica che il sensore sia collegato alla partita richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @throws ValidationException se viene rilevato l'errore «matchId non valido»  
     *  
     */
    private void requireExpectedMatch(Request req) {
        String raw = req.queryParams("matchId");
        if (raw == null) return;
        long expected;
        try { expected = Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new ValidationException("matchId non valido"); }
        if (expected <= 0) throw new ValidationException("matchId non valido");
        if (!java.util.Objects.equals(engine.statoCorrente().matchId(), expected))
            throw new it.uniupo.pissir.darts.common.exception.ConflictException("PARTITA_DIVERSA",
                    "Questo simulatore non è collegato alla partita #" + expected + ". Attendi l'avvio o scegli il bersaglio corretto.");
    }

    /**
     * Interrompe la connessione MQTT del sensore.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «secondi deve essere un numero positivo»  
     *  
     */
    private Object disconnetti(Request req, Response res) {
        requireExpectedMatch(req);
        DisconnettiRequest body = JsonSupport.fromJson(req.body(), DisconnettiRequest.class);
        if (body.secondi() == null || body.secondi() <= 0) {
            throw new ValidationException("secondi deve essere un numero positivo");
        }
        try {
            engine.disconnetti(body.secondi());
        } catch (MqttException e) {
            throw new RuntimeException("Disconnessione fallita", e);
        }
        res.status(204);
        return "";
    }
}
