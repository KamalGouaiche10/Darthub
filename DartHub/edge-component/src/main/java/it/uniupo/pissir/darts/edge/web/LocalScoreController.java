package it.uniupo.pissir.darts.edge.web;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.edge.service.LocalMatchService;
import it.uniupo.pissir.darts.edge.service.LocalMatchService.NuovoPartecipante;
import it.uniupo.pissir.darts.edge.service.MatchSessionManager;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Espone stato e comandi della partita sul monitor locale dell'edge.
 *
 * @see it.uniupo.pissir.darts.edge.service.MatchSessionManager
 *  
 */
public final class LocalScoreController {

    private final MatchSessionManager matchSessionManager;
    private final LocalMatchService localMatchService;
    private final String adminToken;
    private final java.util.function.Supplier<?> configuration;

    /**
     * Inizializza {@code LocalScoreController} con i dati e le dipendenze indicati.
     *
     * @param matchSessionManager partita session manager da utilizzare
     * @param localMatchService componente {@link it.uniupo.pissir.darts.edge.service.LocalMatchService} utilizzato dall'operazione
     * @param adminToken token dell'amministratore di prova
     * @see it.uniupo.pissir.darts.edge.service.MatchSessionManager
     *  
     */
    public LocalScoreController(MatchSessionManager matchSessionManager, LocalMatchService localMatchService,
                                String adminToken) {
        this(matchSessionManager, localMatchService, adminToken, Map::of);
    }

    /**
     * Inizializza {@code LocalScoreController} con i dati e le dipendenze indicati.
     *
     * @param matchSessionManager partita session manager da utilizzare
     * @param localMatchService componente {@link it.uniupo.pissir.darts.edge.service.LocalMatchService} utilizzato dall'operazione
     * @param adminToken token dell'amministratore di prova
     * @param configuration configurazione da applicare
     * @see it.uniupo.pissir.darts.edge.service.MatchSessionManager
     *  
     */
    public LocalScoreController(MatchSessionManager matchSessionManager, LocalMatchService localMatchService,
                                String adminToken, java.util.function.Supplier<?> configuration) {
        this.matchSessionManager = matchSessionManager;
        this.localMatchService = localMatchService;
        this.adminToken = adminToken;
        this.configuration = configuration;
    }

    /**
     * Raccoglie i dati della richiesta relativa a nuovo partecipante.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param playerId identificativo del giocatore
     * @param nomeVisualizzato nome da mostrare nell'interfaccia
     *  
     */
    public record NuovoPartecipanteRequest(Long playerId, String nomeVisualizzato) {
    }

    /**
     * Raccoglie i dati della richiesta relativa a crea partita.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param partecipanti partecipanti associati alla partita
     * @see java.util.List
     *  
     */
    public record CreaPartitaRequest(List<NuovoPartecipanteRequest> partecipanti) {
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.get("/display/punteggio", this::punteggio);
        Spark.get("/display/configurazione", (req, res) -> {
            requireAdmin(req); res.type("application/json"); return JsonSupport.toJson(configuration.get());
        });
        Spark.post("/display/partite", this::creaPartita);
        Spark.post("/display/partite/chiudi", this::chiudiPartita);
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
        res.type("application/json");
        return JsonSupport.toJson(matchSessionManager.statoCorrente());
    }

    /**
     * Crea una partita con i dati e i partecipanti indicati.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «Servono almeno un partecipante»  
     *  
     */
    private Object creaPartita(Request req, Response res) {
        requireAdmin(req);
        CreaPartitaRequest body = JsonSupport.fromJson(req.body(), CreaPartitaRequest.class);
        if (body.partecipanti() == null || body.partecipanti().isEmpty()) {
            throw new ValidationException("Servono almeno un partecipante");
        }
        List<NuovoPartecipante> partecipanti = body.partecipanti().stream()
                .map(p -> new NuovoPartecipante(p.playerId(), p.nomeVisualizzato()))
                .toList();
        var match = localMatchService.creaPartitaLocale(partecipanti);
        res.status(201);
        res.type("application/json");
        return JsonSupport.toJson(Map.of("localUuid", match.localUuid()));
    }

    /**
     * Chiude partita per il locale punteggio.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP  
     *  
     */
    private Object chiudiPartita(Request req, Response res) {
        requireAdmin(req);
        localMatchService.chiudiPartitaLocale();
        res.status(204);
        return "";
    }

    /**
     * Verifica il requisito amministratore prima di eseguire l'operazione.
     *
     * @param req richiesta HTTP ricevuta
     * @throws UnauthorizedException se viene rilevato l'errore «Token amministrativo del display mancante o non valido»  
     *  
     */
    private void requireAdmin(Request req) {
        if (!isAdmin(req.headers("X-Display-Token"))) {
            throw new UnauthorizedException("Token amministrativo del display mancante o non valido");
        }
    }

    /**
     * Verifica se l'identità possiede un ruolo amministrativo.
     *
     * @param supplied valore fornito alla verifica
     * @return {@code true} se l'identità possiede un ruolo amministrativo; {@code false} altrimenti
     *  
     */
    boolean isAdmin(String supplied) {
        return adminToken != null && !adminToken.isBlank() && supplied != null
                && MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }
}
