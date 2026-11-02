package it.uniupo.pissir.darts.tournament.service;

import com.google.gson.reflect.TypeToken;
import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea partite e ne legge il gameTypeId chiamando match-service; recupera l'unica ScoringRule da usare per le partite del torneo chiamando game-service.
 *
 * @see java.lang.reflect.Type
 *  
 */
public final class MatchServiceClient {

    private static final Type SCORING_RULE_PAGE_TYPE = TypeToken.getParameterized(PagedResult.class, ScoringRuleDTO.class).getType();

    private final ServiceHttpClient httpClient;
    private final String matchServiceBaseUrl;
    private final String gameServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;
    private final Map<Long, Long> scoringRuleCache = new ConcurrentHashMap<>();

    /**
     * Inizializza {@code MatchServiceClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param matchServiceBaseUrl indirizzo di base della partita servizio
     * @param gameServiceBaseUrl indirizzo di base del gioco servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public MatchServiceClient(ServiceHttpClient httpClient, String matchServiceBaseUrl, String gameServiceBaseUrl,
                               ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.matchServiceBaseUrl = matchServiceBaseUrl;
        this.gameServiceBaseUrl = gameServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Legge il gioco associato alla risorsa.
     *
     * @param matchId identificativo della partita
     * @return il valore numerico calcolato o letto
     *  
     */
    public long gameTypeIdOf(long matchId) {
        return partita(matchId).gameTypeId();
    }

    /**
     * Legge lo stato della risorsa.
     *
     * @param matchId identificativo della partita
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String statoOf(long matchId) {
        return partita(matchId).stato();
    }

    /**
     * Legge il bersaglio associato alla risorsa.
     *
     * @param matchId identificativo della partita
     * @return il valore numerico calcolato o letto
     *  
     */
    public long dartboardIdOf(long matchId) { return partita(matchId).dartboardId(); }

    /**
     * Avvia il torneo e ne prepara gli incontri.
     *
     * @param matchId identificativo della partita
     *  
     */
    public void startTournament(long matchId) {
        httpClient.post(matchServiceBaseUrl + "/partite/" + matchId + "/avvia-torneo", Map.of(),
                serviceTokenClient.token(), Object.class);
    }

    /**
     * Determina l'esito della partita in caso di assenza.
     *
     * @param matchId identificativo della partita
     * @param winnerPlayerId identificativo del giocatore vincitore
     *  
     */
    public void resolveAbsence(long matchId, Long winnerPlayerId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("winnerPlayerId", winnerPlayerId);
        httpClient.post(matchServiceBaseUrl + "/partite/" + matchId + "/assenza", body,
                serviceTokenClient.token(), Object.class);
    }

    /**
     * Compone l'evento del risultato conclusivo della partita.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public MatchCompletedEventDTO completedEvent(long matchId) {
        return httpClient.get(matchServiceBaseUrl + "/partite/" + matchId + "/esito-torneo",
                serviceTokenClient.token(), MatchCompletedEventDTO.class);
    }

    /**
     * Recupera i dati della partita dal servizio remoto.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.service.MatchServiceClient.MatchDTO
     *  
     */
    private MatchDTO partita(long matchId) {
        return httpClient.get(matchServiceBaseUrl + "/partite/" + matchId, serviceTokenClient.token(), MatchDTO.class);
    }

    /**
     * Crea una partita con i dati e i partecipanti indicati.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param modalita modalità della partita o del sensore
     * @param idempotencyKey chiave di riconoscimento della richiesta già elaborata
     * @return il valore numerico calcolato o letto
     *  
     */
    public long creaPartita(long dartboardId, long localeId, long gameTypeId, String modalita,
                           String idempotencyKey) {
        long scoringRuleId = scoringRuleCache.computeIfAbsent(gameTypeId, this::primaRegolaDisponibile);
        Map<String, Object> body = Map.of("dartboardId", dartboardId, "localeId", localeId, "gameTypeId", gameTypeId,
                "scoringRuleId", scoringRuleId, "modalita", modalita, "idempotencyKey", idempotencyKey);
        MatchDTO creata = httpClient.post(matchServiceBaseUrl + "/partite", body, serviceTokenClient.token(), MatchDTO.class);
        return creata.id();
    }

    /**
     * Verifica il requisito squadra exists prima di eseguire l'operazione.
     *
     * @param teamId identificativo della squadra
     *  
     */
    public void requireTeamExists(long teamId) {
        httpClient.get(matchServiceBaseUrl + "/squadre/" + teamId, serviceTokenClient.token(), Object.class);
    }

    /**
     * Verifica se il giocatore appartiene alla squadra.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     * @return {@code true} se il giocatore appartiene alla squadra; {@code false} altrimenti
     *  
     */
    public boolean teamContains(long teamId, long playerId) {
        try {
            TeamDTO team = httpClient.get(matchServiceBaseUrl + "/squadre/" + teamId,
                    serviceTokenClient.token(), TeamDTO.class);
            return team.membri() != null && team.membri().contains(playerId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Recupera il nome della squadra.
     *
     * @param teamId identificativo della squadra
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String teamName(long teamId) {
        try {
            TeamDTO team = httpClient.get(matchServiceBaseUrl + "/squadre/" + teamId,
                    serviceTokenClient.token(), TeamDTO.class);
            return team.nome() == null || team.nome().isBlank() ? "Squadra #" + teamId : team.nome();
        } catch (RuntimeException e) {
            return "Squadra #" + teamId;
        }
    }

    /**
     * Raccoglie i dati di squadra.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param nome nome della risorsa
     * @param membri giocatori appartenenti alla squadra
     * @see java.util.List
     *  
     */
    private record TeamDTO(String nome, java.util.List<Long> membri) { }

    /**
     * Aggiunge partecipante per la partita.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     *  
     */
    public void aggiungiPartecipante(long matchId, Long playerId, Long teamId) {
        RosterEntry[] roster = httpClient.get(matchServiceBaseUrl + "/partite/" + matchId + "/roster",
                serviceTokenClient.token(), RosterEntry[].class);
        for (RosterEntry entry : roster) {
            if (playerId != null && playerId.equals(entry.playerId())) return;
            if (teamId != null && teamId.equals(entry.teamId())) return;
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("playerId", playerId);
        body.put("teamId", teamId);
        httpClient.post(matchServiceBaseUrl + "/partite/" + matchId + "/partecipanti", body, serviceTokenClient.token(), Object.class);
    }

    /**
     * Raccoglie i dati di formazione dei partecipanti.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     *  
     */
    private record RosterEntry(Long playerId, Long teamId) { }

    /**
     * Seleziona una regola di punteggio disponibile per il gioco.
     *
     * @param gameTypeId identificativo del gioco
     * @return il valore numerico calcolato o letto
     * @throws BusinessRuleException se viene rilevato l'errore «GIOCO_SENZA_REGOLE»
     *  
     */
    private long primaRegolaDisponibile(long gameTypeId) {
        PagedResult<ScoringRuleDTO> pagina = httpClient.get(
                gameServiceBaseUrl + "/giochi/" + gameTypeId + "/regole?size=1", serviceTokenClient.token(), SCORING_RULE_PAGE_TYPE);
        if (pagina.contenuto().isEmpty()) {
            throw new BusinessRuleException("GIOCO_SENZA_REGOLE",
                    "Il tipo di gioco " + gameTypeId + " non ha nessuna ScoringRule: impossibile generare partite");
        }
        return pagina.contenuto().get(0).id();
    }

    /**
     * Stessa forma di MatchDTO in match-service (solo i campi che servono qui).
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo della partita
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleId identificativo della regola di punteggio
     * @param modalita modalità della partita o del sensore
     * @param stato stato da impostare o verificare
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @param localUuid identificativo univoco della partita creata offline
     * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
     * @param dataInizio data di inizio del torneo
     * @param dataFine data di fine del torneo
     *  
     */
    private record MatchDTO(long id, long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                             String modalita, String stato, long createdBy, String localUuid, long clockOffsetMs,
                             String dataInizio, String dataFine) {
    }

    /**
     * Stessa forma di ScoringRuleDTO in game-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo della regola di punteggio
     * @param gameTypeId identificativo del gioco
     * @param variante nome della variante del gioco
     * @param direzione direzione di variazione del punteggio
     * @param punteggioIniziale punteggio iniziale previsto dalla regola
     * @param punteggioObiettivo punteggio da raggiungere per vincere
     * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
     * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
     * @param maxTiriPerTurno numero massimo di tiri per turno
     *  
     */
    private record ScoringRuleDTO(long id, long gameTypeId, String variante, String direzione,
                                   int punteggioIniziale, int punteggioObiettivo, boolean chiusuraDouble,
                                   boolean bustAnnullaTurno, int maxTiriPerTurno) {
    }
}
