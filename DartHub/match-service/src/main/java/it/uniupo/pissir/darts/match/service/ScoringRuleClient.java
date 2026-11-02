package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recupera una ScoringRule da game-service e la mette in cache in memoria: le regole non cambiano a runtime, quindi una cache semplice, mai invalidata, è sufficiente per la durata del processo.
 *
 * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
 *  
 */
public final class ScoringRuleClient {

    private final ServiceHttpClient httpClient;
    private final String gameServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;
    private final Map<Long, ScoringRule> cache = new ConcurrentHashMap<>();

    /**
     * Inizializza {@code ScoringRuleClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param gameServiceBaseUrl indirizzo di base del gioco servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public ScoringRuleClient(ServiceHttpClient httpClient, String gameServiceBaseUrl, ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.gameServiceBaseUrl = gameServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Recupera dal servizio remoto la regola di punteggio.
     *
     * @param scoringRuleId identificativo della regola di punteggio
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    public ScoringRule fetch(long scoringRuleId) {
        return cache.computeIfAbsent(scoringRuleId, this::fetchRemote);
    }

    /**
     * Recupera remoto per la regola di punteggio.
     *
     * @param id identificativo della regola di punteggio
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    private ScoringRule fetchRemote(long id) {
        RemoteScoringRule remote = httpClient.get(gameServiceBaseUrl + "/regole/" + id, serviceTokenClient.token(), RemoteScoringRule.class);
        return new ScoringRule(remote.id(), remote.gameTypeId(), remote.variante(),
                Direzione.valueOf(remote.direzione()), remote.punteggioIniziale(), remote.punteggioObiettivo(),
                remote.chiusuraDouble(), remote.bustAnnullaTurno(), remote.maxTiriPerTurno(), remote.parametriJson());
    }

    /**
     * Stessa forma del ScoringRuleDTO di game-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del remoto scoring regola
     * @param gameTypeId identificativo del gioco
     * @param variante nome della variante del gioco
     * @param direzione direzione di variazione del punteggio
     * @param punteggioIniziale punteggio iniziale previsto dalla regola
     * @param punteggioObiettivo punteggio da raggiungere per vincere
     * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
     * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
     * @param maxTiriPerTurno numero massimo di tiri per turno
     * @param parametriJson parametri aggiuntivi della regola in JSON
     *  
     */
    private record RemoteScoringRule(long id, long gameTypeId, String variante, String direzione,
                                      int punteggioIniziale, int punteggioObiettivo, boolean chiusuraDouble,
                                      boolean bustAnnullaTurno, int maxTiriPerTurno, String parametriJson) {
    }
}
