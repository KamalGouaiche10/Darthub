package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.game.engine.Esito;
import it.uniupo.pissir.darts.game.engine.MatchState;
import it.uniupo.pissir.darts.game.engine.RuleEngine;
import it.uniupo.pissir.darts.game.engine.RuleEngineRegistry;
import it.uniupo.pissir.darts.game.engine.RuleOutcome;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.dto.ScoreDTO;
import it.uniupo.pissir.darts.match.dto.ScorePushDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.Throw;
import it.uniupo.pissir.darts.match.model.ThrowOrigin;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Valida, interroga il RuleEngine risolto per il motore della partita, applica l'esito di ogni evento di gioco — da MQTT o da REST — tutto in una transazione. 
 */
public final class ThrowIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(ThrowIngestionService.class);
    private static final String MOTORE_DARTS = "darts";

    private final ConnectionProvider connectionProvider;
    private final MatchDao matchDao;
    private final MatchParticipantDao participantDao;
    private final ThrowDao throwDao;
    private final ScoringRuleClient scoringRuleClient;
    private final GameTypeClient gameTypeClient;
    private final MatchResultService matchResultService;
    private final MatchEventPublisher matchEventPublisher;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final MatchLockRegistry matchLockRegistry;
    private final TeamNameResolver teamNameResolver;
    private final PlayerNameResolver playerNameResolver;

    /**
     * Inizializza {@code ThrowIngestionService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param matchDao componente {@link it.uniupo.pissir.darts.match.dao.MatchDao} utilizzato dall'operazione
     * @param participantDao componente {@link it.uniupo.pissir.darts.match.dao.MatchParticipantDao} utilizzato dall'operazione
     * @param throwDao componente {@link it.uniupo.pissir.darts.match.dao.ThrowDao} utilizzato dall'operazione
     * @param scoringRuleClient componente {@link it.uniupo.pissir.darts.match.service.ScoringRuleClient} utilizzato dall'operazione
     * @param gameTypeClient componente {@link it.uniupo.pissir.darts.match.service.GameTypeClient} utilizzato dall'operazione
     * @param matchResultService componente {@link it.uniupo.pissir.darts.match.service.MatchResultService} utilizzato dall'operazione
     * @param matchEventPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher} utilizzato dall'operazione
     * @param edgeCommandPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher} utilizzato dall'operazione
     * @param matchLockRegistry componente {@link it.uniupo.pissir.darts.match.service.MatchLockRegistry} utilizzato dall'operazione
     * @param teamNameResolver squadra nome resolver da utilizzare
     * @param playerNameResolver giocatore nome resolver da utilizzare
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public ThrowIngestionService(ConnectionProvider connectionProvider, MatchDao matchDao,
                                  MatchParticipantDao participantDao, ThrowDao throwDao,
                                  ScoringRuleClient scoringRuleClient, GameTypeClient gameTypeClient,
                                  MatchResultService matchResultService, MatchEventPublisher matchEventPublisher,
                                  EdgeCommandPublisher edgeCommandPublisher, MatchLockRegistry matchLockRegistry,
                                  TeamNameResolver teamNameResolver, PlayerNameResolver playerNameResolver) {
        this.connectionProvider = connectionProvider;
        this.matchDao = matchDao;
        this.participantDao = participantDao;
        this.throwDao = throwDao;
        this.scoringRuleClient = scoringRuleClient;
        this.gameTypeClient = gameTypeClient;
        this.matchResultService = matchResultService;
        this.matchEventPublisher = matchEventPublisher;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.matchLockRegistry = matchLockRegistry;
        this.teamNameResolver = teamNameResolver;
        this.playerNameResolver = playerNameResolver;
    }

    /**
     * Valida ed elabora l'evento ricevuto dal sensore.
     *
     * @param evento evento da elaborare
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    public boolean ingerisci(GameEvent evento) {
        ReentrantLock lock = matchLockRegistry.lockFor(evento.matchId());
        lock.lock();
        try {
            return ingerisciSerializzato(evento);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Elabora l'evento mentre è detenuto il lock della partita.
     *
     * @param evento evento da elaborare
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    private boolean ingerisciSerializzato(GameEvent evento) {
        long matchId = evento.matchId();
        long participantId = evento.participantId();
        Match match = matchDao.findById(matchId).orElse(null);
        if (match == null || match.stato() != MatchStatus.IN_CORSO) {
            LOG.info("Evento scartato: partita {} inesistente o non IN_CORSO", matchId);
            return false;
        }
        ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());
        String motore = gameTypeClient.fetchMotore(match.gameTypeId());
        RuleEngine engine = RuleEngineRegistry.resolve(motore);
        boolean modoDarts = MOTORE_DARTS.equals(motore);

        AtomicBoolean accettato = new AtomicBoolean(false);
        AtomicReference<RuleOutcome> esitoRef = new AtomicReference<>();
        AtomicReference<List<MatchParticipant>> partecipantiRef = new AtomicReference<>();
        AtomicReference<List<MatchResult>> risultatiChiusuraRef = new AtomicReference<>();

        connectionProvider.withTransaction(connection -> {
            List<MatchParticipant> attivi = participantDao.findAttiviByMatch(matchId);
            MatchParticipant partecipante = attivi.stream().filter(p -> p.id() == participantId).findFirst().orElse(null);
            if (partecipante == null) {
                LOG.info("Evento scartato: partecipante {} non attivo nella partita {}", participantId, matchId);
                return null;
            }

            int numeroTurno = 0;
            MatchState statoCorrente;
            if (modoDarts) {
                List<Throw> rilevanti = throwDao.findRilevantiByMatch(connection, matchId);
                TurnManager.TurnState turno = TurnManager.calcolaTurnoCorrente(attivi, rilevanti, regola);
                if (turno.participantId() != participantId) {
                    LOG.info("Evento scartato: non è il turno del partecipante {} (turno di {})",
                            participantId, turno.participantId());
                    return null;
                }
                numeroTurno = turno.numeroTurno();
                Map<String, Object> extra = Map.of(
                        "puntiInizioTurno", turno.puntiInizioTurno(),
                        "tiriGiaFattiNelTurno", turno.tiriGiaFattiNelTurno());
                statoCorrente = new MatchState(punteggiDi(attivi), numeroTurno, participantId, extra);
            } else {
                statoCorrente = new MatchState(punteggiDi(attivi), 0, participantId, Map.of());
            }

            RuleOutcome esito = engine.applica(regola, statoCorrente, evento);

            String eventId = UUID.randomUUID().toString();
            Instant timestampRicezione = Instant.now();
            DartSector settore = modoDarts ? DartSector.valueOf(String.valueOf(evento.payload().get("settore"))) : null;
            Multiplier moltiplicatore = modoDarts
                    ? Multiplier.valueOf(String.valueOf(evento.payload().get("moltiplicatore"))) : null;

            int punti = modoDarts
                    ? it.uniupo.pissir.darts.game.model.darts.ScoreCalculator.punti(settore, moltiplicatore).orElse(0)
                    : esito.nuovoStato().punteggioDi(participantId) - statoCorrente.punteggioDi(participantId);

            throwDao.insert(connection, matchId, participantId, evento.tipo(), settore, moltiplicatore, null,
                    partecipante.teamId(), punti, numeroTurno, esitoPersistito(esito.esito()), evento.timestampSensore(),
                    timestampRicezione, ThrowOrigin.LIVE, dartboardIdDi(match));

            if (modoDarts && Boolean.TRUE.equals(esito.extra().get("turnoAnnullato"))) {
                throwDao.annullaTurno(connection, participantId, numeroTurno);
            }

            MatchParticipant aggiornato = new MatchParticipant(partecipante.id(), partecipante.matchId(),
                    partecipante.playerId(), partecipante.teamId(), partecipante.ordineTurno(),
                    esito.nuovoStato().punteggioDi(participantId), partecipante.ritirato());
            participantDao.update(connection, aggiornato);

            List<MatchParticipant> attiviAggiornati = attivi.stream()
                    .map(p -> p.id() == aggiornato.id() ? aggiornato : p)
                    .toList();
            partecipantiRef.set(attiviAggiornati);

            if (esito.esito() == Esito.VITTORIA) {
                risultatiChiusuraRef.set(matchResultService.chiudiSuConnessione(connection, match, attiviAggiornati,
                        regola, participantId));
            }

            esitoRef.set(new RuleOutcome(esito.esito(), esito.nuovoStato(), esito.vincitoreParticipantId(),
                    esito.motivo(), Map.of("eventId", eventId, "punti", punti)));
            accettato.set(true);
            return null;
        });

        if (!accettato.get()) {
            return false;
        }

        pubblicaEventiPostEvento(match, regola, modoDarts, participantId, evento, esitoRef.get(), partecipantiRef.get());

        List<MatchResult> risultatiChiusura = risultatiChiusuraRef.get();
        if (risultatiChiusura != null) {
            Match completata = matchDao.findById(matchId).orElse(match);
            matchResultService.pubblicaChiusura(completata, partecipantiRef.get(), risultatiChiusura);
        }
        return true;
    }

    /**
     * Pubblica eventi post evento tramite il collegamento MQTT.
     *
     * @param match partita da elaborare
     * @param regola regola di punteggio da applicare
     * @param modoDarts modo darts da utilizzare
     * @param participantId identificativo del partecipante
     * @param evento evento da elaborare
     * @param esito esito del tiro o della partita
     * @param partecipanti partecipanti associati alla partita
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private void pubblicaEventiPostEvento(Match match, ScoringRule regola, boolean modoDarts, long participantId,
                                           GameEvent evento, RuleOutcome esito, List<MatchParticipant> partecipanti) {
        MatchParticipant autoreEvento = partecipanti.stream().filter(p -> p.id() == participantId).findFirst().orElse(null);
        String eventId = String.valueOf(esito.extra().get("eventId"));
        int punti = (int) esito.extra().get("punti");
        GameEvent eventoValutato = evento.conMatchEPartecipante(match.id(), participantId)
                .valutato(eventId, autoreEvento == null ? null : autoreEvento.playerId(),
                        autoreEvento == null ? null : autoreEvento.teamId(),
                        punti, ThrowOrigin.LIVE.name(), esito.esito().name());
        matchEventPublisher.publishThrowEvent(eventoValutato);

        Long giocatoreAttivo = null;
        if (modoDarts && esito.esito() != Esito.VITTORIA) {
            List<it.uniupo.pissir.darts.match.model.Throw> rilevantiDopo = connectionProvider.withConnection(
                    connection -> throwDao.findRilevantiByMatch(connection, match.id()));
            giocatoreAttivo = TurnManager.calcolaTurnoCorrente(partecipanti, rilevantiDopo, regola).participantId();
        }

        List<ScoreDTO.ParticipantScoreDTO> punteggi = partecipanti.stream()
                .map(p -> new ScoreDTO.ParticipantScoreDTO(p.id(), p.playerId(), p.teamId(),
                        teamNameResolver.resolveOrNull(p.teamId()),
                        p.playerId() == null ? null : playerNameResolver.resolve(p.playerId()),
                        p.ordineTurno(), p.punteggioCorrente(), p.ritirato()))
                .toList();
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:")
                || matchDao.isSensorStarted(match.id()))
            edgeCommandPublisher.publishScore(match.localeId(), dartboardIdDi(match),
                    new ScorePushDTO(match.id(), punteggi, giocatoreAttivo, esito.esito().name()));
    }

    /**
     * Legge i punteggi dei partecipanti alla partita.
     *
     * @param partecipanti partecipanti associati alla partita
     * @return la mappa dei dati elaborati
     * @see java.util.Map
     *  
     */
    private static Map<Long, Integer> punteggiDi(List<MatchParticipant> partecipanti) {
        Map<Long, Integer> punteggi = new HashMap<>();
        for (MatchParticipant partecipante : partecipanti) {
            punteggi.put(partecipante.id(), partecipante.punteggioCorrente());
        }
        return punteggi;
    }

    /**
     * Recupera il bersaglio associato alla partita.
     *
     * @param match partita da elaborare
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private static long dartboardIdDi(Match match) {
        return match.dartboardId();
    }

    /**
     * Legge l'esito del tiro già registrato.
     *
     * @param esito esito del tiro o della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.darts.Esito} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.darts.Esito
     *  
     */
    private static it.uniupo.pissir.darts.game.model.darts.Esito esitoPersistito(Esito esito) {
        return switch (esito) {
            case VALIDO, FINE_PARTITA -> it.uniupo.pissir.darts.game.model.darts.Esito.VALIDO;
            case BUST -> it.uniupo.pissir.darts.game.model.darts.Esito.BUST;
            case VITTORIA -> it.uniupo.pissir.darts.game.model.darts.Esito.VITTORIA;
            case IGNORATO -> it.uniupo.pissir.darts.game.model.darts.Esito.IGNORATO;
        };
    }
}
