package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchOrigin;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.ScoringRuleSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crea e chiude partite ANCHE SENZA SERVER: genera un local_uuid, legge la ScoringRule dalla RuleCache, accetta partecipanti registrati (playerId noto) e ospiti (solo nome visualizzato).
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class LocalMatchService {

    private final ConnectionProvider connectionProvider;
    private final LocalMatchDao localMatchDao;
    private final RuleCache ruleCache;
    private final MatchSessionManager matchSessionManager;
    private final long dartboardId;

    /**
     * Inizializza {@code LocalMatchService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param localMatchDao componente {@link it.uniupo.pissir.darts.edge.dao.LocalMatchDao} utilizzato dall'operazione
     * @param ruleCache componente {@link it.uniupo.pissir.darts.edge.service.RuleCache} utilizzato dall'operazione
     * @param matchSessionManager partita session manager da utilizzare
     * @param dartboardId identificativo del bersaglio
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public LocalMatchService(ConnectionProvider connectionProvider, LocalMatchDao localMatchDao, RuleCache ruleCache,
                              MatchSessionManager matchSessionManager, long dartboardId) {
        this.connectionProvider = connectionProvider;
        this.localMatchDao = localMatchDao;
        this.ruleCache = ruleCache;
        this.matchSessionManager = matchSessionManager;
        this.dartboardId = dartboardId;
    }

    /**
     * Raccoglie i dati di nuovo partecipante.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param playerId identificativo del giocatore
     * @param nomeVisualizzato nome da mostrare nell'interfaccia
     *  
     */
    public record NuovoPartecipante(Long playerId, String nomeVisualizzato) {
    }

    /**
     * Crea una partita sull'edge usando i dati disponibili localmente.
     *
     * @param partecipanti partecipanti associati alla partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.model.LocalMatch} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «Una partita è già in corso su questo bersaglio»
     * @throws ValidationException se viene rilevato l'errore «Servono almeno un partecipante»
     * @throws BusinessRuleException se viene rilevato l'errore «Serve che l'edge abbia già ricevuto almeno un comando START_MATCH dal server»
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    public LocalMatch creaPartitaLocale(List<NuovoPartecipante> partecipanti) {
        if (localMatchDao.findInCorso().isPresent()) {
            throw new ConflictException("PARTITA_GIA_IN_CORSO", "Una partita è già in corso su questo bersaglio");
        }
        if (partecipanti == null || partecipanti.isEmpty()) {
            throw new ValidationException("Servono almeno un partecipante");
        }
        String regolaJson = ruleCache.currentScoringRuleJson().orElseThrow(() -> new BusinessRuleException(
                "REGOLA_NON_IN_CACHE",
                "Nessuna regola di punteggio in cache: impossibile avviare una partita offline. "
                        + "Serve che l'edge abbia già ricevuto almeno un comando START_MATCH dal server."));
        int puntiIniziali = leggiPunteggioIniziale(regolaJson);

        String localUuid = UUID.randomUUID().toString();
        Instant adesso = Instant.now();
        LocalMatch match = new LocalMatch(localUuid, null, dartboardId, regolaJson, LocalMatchOrigin.LOCALE,
                LocalMatchStatus.IN_CORSO, false, adesso, null);

        connectionProvider.withTransaction(connection -> {
            localMatchDao.insert(connection, match);
            int ordine = 1;
            for (NuovoPartecipante partecipante : partecipanti) {
                localMatchDao.insertParticipant(connection, localUuid, null, partecipante.playerId(),
                        partecipante.nomeVisualizzato(), ordine, puntiIniziali);
                ordine++;
            }
            return null;
        });
        return match;
    }

    /**
     * Chiude partita locale per la partita locale.
     *
     * @throws ConflictException se viene rilevato l'errore «Nessuna partita in corso da chiudere»
     *  
     */
    public void chiudiPartitaLocale() {
        if (localMatchDao.findInCorso().isEmpty()) {
            throw new ConflictException("NESSUNA_PARTITA_IN_CORSO", "Nessuna partita in corso da chiudere");
        }
        matchSessionManager.chiudiPartitaInCorso();
    }

    /**
     * Legge punteggio iniziale per la partita locale.
     *
     * @param regolaJson regola di punteggio in JSON
     * @return il valore numerico calcolato o letto
     *  
     */
    private int leggiPunteggioIniziale(String regolaJson) {
        return GsonFactory.instance().fromJson(regolaJson, ScoringRuleSnapshot.class).punteggioIniziale();
    }
}
