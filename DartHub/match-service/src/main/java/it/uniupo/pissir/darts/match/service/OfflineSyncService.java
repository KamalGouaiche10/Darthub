package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.DartsRuleEngine;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.game.model.darts.ScoreCalculator;
import it.uniupo.pissir.darts.game.model.darts.ThrowOutcome;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.dto.ImportOfflineRequestDTO;
import it.uniupo.pissir.darts.match.dto.ImportParticipantDTO;
import it.uniupo.pissir.darts.match.dto.MatchDTO;
import it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO;
import it.uniupo.pissir.darts.match.dto.SyncResultDTO;
import it.uniupo.pissir.darts.match.dto.ThrowBatchItemDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.model.Throw;
import it.uniupo.pissir.darts.match.model.ThrowOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sincronizzazione offline. 
 */
public final class OfflineSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineSyncService.class);

    private final ConnectionProvider connectionProvider;
    private final MatchDao matchDao;
    private final MatchParticipantDao participantDao;
    private final ThrowDao throwDao;
    private final MatchResultDao matchResultDao;
    private final ScoringRuleClient scoringRuleClient;
    private final MatchLockRegistry matchLockRegistry;
    private final MatchResultService matchResultService;
    private final DartboardStatusClient dartboardStatusClient;

    /**
     * Inizializza {@code OfflineSyncService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param matchDao componente {@link it.uniupo.pissir.darts.match.dao.MatchDao} utilizzato dall'operazione
     * @param participantDao componente {@link it.uniupo.pissir.darts.match.dao.MatchParticipantDao} utilizzato dall'operazione
     * @param throwDao componente {@link it.uniupo.pissir.darts.match.dao.ThrowDao} utilizzato dall'operazione
     * @param matchResultDao componente {@link it.uniupo.pissir.darts.match.dao.MatchResultDao} utilizzato dall'operazione
     * @param scoringRuleClient componente {@link it.uniupo.pissir.darts.match.service.ScoringRuleClient} utilizzato dall'operazione
     * @param matchLockRegistry componente {@link it.uniupo.pissir.darts.match.service.MatchLockRegistry} utilizzato dall'operazione
     * @param matchResultService componente {@link it.uniupo.pissir.darts.match.service.MatchResultService} utilizzato dall'operazione
     * @param dartboardStatusClient componente {@link it.uniupo.pissir.darts.match.service.DartboardStatusClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public OfflineSyncService(ConnectionProvider connectionProvider, MatchDao matchDao,
                               MatchParticipantDao participantDao, ThrowDao throwDao, MatchResultDao matchResultDao,
                               ScoringRuleClient scoringRuleClient, MatchLockRegistry matchLockRegistry,
                               MatchResultService matchResultService, DartboardStatusClient dartboardStatusClient) {
        this.connectionProvider = connectionProvider;
        this.matchDao = matchDao;
        this.participantDao = participantDao;
        this.throwDao = throwDao;
        this.matchResultDao = matchResultDao;
        this.scoringRuleClient = scoringRuleClient;
        this.matchLockRegistry = matchLockRegistry;
        this.matchResultService = matchResultService;
        this.dartboardStatusClient = dartboardStatusClient;
    }

    /**
     * Riconcilia con il server i tiri conservati durante la disconnessione.
     *
     * @param dartboardId identificativo del bersaglio
     * @param matchId identificativo della partita
     * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
     * @param tiri tiri associati alla partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.SyncResultDTO} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Partita non trovata»
     * @see it.uniupo.pissir.darts.match.dto.SyncResultDTO
     *  
     */
    public SyncResultDTO syncOffline(long dartboardId, long matchId, Long clockOffsetMs, List<ThrowBatchItemDTO> tiri) {
        Match match = matchDao.findById(matchId)
                .orElseThrow(() -> new NotFoundException("PARTITA_NON_TROVATA", "Partita non trovata"));
        if (match.dartboardId() != dartboardId) {
            throw new it.uniupo.pissir.darts.common.exception.ForbiddenException("BERSAGLIO_NON_PROPRIO",
                    "La partita non appartiene al bersaglio indicato");
        }
        ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());

        ReentrantLock lock = matchLockRegistry.lockFor(matchId);
        lock.lock();
        try {
            Match corrente = matchDao.findById(matchId).orElseThrow();
            int[] contatori = connectionProvider.withTransaction(connection -> {
                int accettati = 0;
                int scartati = 0;
                for (ThrowBatchItemDTO item : tiri) {
                    boolean inserito = inserisciConIdRisolto(connection, matchId, dartboardId, item, item.participantId());
                    if (inserito) {
                        accettati++;
                    } else {
                        scartati++;
                    }
                }

                ricalcolaDaZero(connection, corrente, regola, false);

                boolean conclusa = matchResultDao.existsForMatch(connection, matchId);
                long offset = clockOffsetMs != null ? clockOffsetMs : corrente.clockOffsetMs();
                MatchStatus stato = conclusa
                        ? (corrente.stato() == MatchStatus.COMPLETATA ? MatchStatus.COMPLETATA : MatchStatus.SINCRONIZZATA)
                        : (corrente.stato() == MatchStatus.SINCRONIZZATA ? MatchStatus.IN_CORSO : corrente.stato());
                Match aggiornata = new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                        match.scoringRuleId(), match.modalita(), stato, match.createdBy(),
                        match.localUuid(), offset, match.dataInizio(),
                        conclusa ? (corrente.dataFine() != null ? corrente.dataFine() : Instant.now())
                                : (stato == MatchStatus.IN_CORSO ? null : corrente.dataFine()));
                matchDao.update(connection, aggiornata);
                return new int[]{accettati, scartati};
            });

            Match finale = matchDao.findById(matchId).orElseThrow();
            List<MatchResult> risultati = matchResultDao.findByMatch(matchId);
            if (!risultati.isEmpty()) {
                matchResultService.pubblicaChiusura(finale, participantDao.findByMatch(matchId), risultati);
            }
            return new SyncResultDTO(contatori[0], contatori[1], matchId, finale.stato().name());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Importa sul server una partita creata localmente.
     *
     * @param richiesta richiesta da utilizzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO importOffline(ImportOfflineRequestDTO richiesta) {
        var esistente = matchDao.findByLocalUuid(richiesta.localUuid());
        if (esistente.isPresent()) {
            return toDto(esistente.get());
        }

        ScoringRuleSnapshotDTO snapshot = richiesta.scoringRule();

        ScoringRule regola = new ScoringRule(0, richiesta.gameTypeId(), snapshot.variante(),
                Direzione.valueOf(snapshot.direzione()), snapshot.punteggioIniziale(), snapshot.punteggioObiettivo(),
                snapshot.chiusuraDouble(), snapshot.bustAnnullaTurno(), snapshot.maxTiriPerTurno(), "{}");

        Instant adesso = Instant.now();
        long createdBy = primoGiocatoreONull(richiesta);
        long clockOffsetMs = richiesta.clockOffsetMs() != null ? richiesta.clockOffsetMs() : 0;

        Match creata = connectionProvider.withTransaction(connection -> {
            long matchId = matchDao.insertOffline(connection, richiesta.dartboardId(), richiesta.localeId(),
                    richiesta.gameTypeId(), 0, ModalitaPartita.INDIVIDUALE, createdBy, richiesta.localUuid(),
                    clockOffsetMs, adesso);

            Match match = new Match(matchId, richiesta.dartboardId(), richiesta.localeId(), richiesta.gameTypeId(),
                    0, ModalitaPartita.INDIVIDUALE, MatchStatus.SINCRONIZZATA, createdBy, richiesta.localUuid(),
                    clockOffsetMs, adesso, adesso);

            Map<Integer, Long> participantIdPerOrdine = new HashMap<>();
            for (ImportParticipantDTO partecipante : richiesta.partecipanti()) {
                long participantId = participantDao.insert(connection, matchId, partecipante.playerId(), null,
                        partecipante.ordineTurno());
                participantIdPerOrdine.put(partecipante.ordineTurno(), participantId);
            }

            for (ThrowBatchItemDTO item : richiesta.tiri()) {
                Long participantIdReale = participantIdPerOrdine.get(item.participantId() == null ? null
                        : item.participantId().intValue());
                if (participantIdReale == null) {
                    LOG.warn("Tiro offline riferisce l'ordine di turno {} senza partecipante corrispondente: scartato",
                            item.participantId());
                    continue;
                }
                inserisciConIdRisolto(connection, matchId, richiesta.dartboardId(), item, participantIdReale);
            }

            ricalcolaDaZero(connection, match, regola, true);
            return match;
        });

        List<MatchParticipant> partecipanti = participantDao.findByMatch(creata.id());
        List<MatchResult> risultati = matchResultDao.findByMatch(creata.id());

        dartboardStatusClient.setDisponibile(creata.dartboardId());
        matchResultService.pubblicaChiusura(creata, partecipanti, risultati);

        return toDto(creata);
    }

    /**
     * Seleziona il primo giocatore disponibile, oppure restituisce un valore assente.
     *
     * @param richiesta richiesta da utilizzare
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.match.dto.ImportOfflineRequestDTO
     *  
     */
    private Long primoGiocatoreONull(ImportOfflineRequestDTO richiesta) {
        return richiesta.partecipanti().stream()
                .map(ImportParticipantDTO::playerId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0L);
    }

    /**
     * Inserisce con identificativo risolto per il offline sync.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param matchId identificativo della partita
     * @param dartboardId identificativo del bersaglio
     * @param item item da utilizzare
     * @param participantId identificativo del partecipante
     * @return l'esito della condizione verificata
     * @see java.sql.Connection
     *  
     */
    private boolean inserisciConIdRisolto(Connection connection, long matchId, long dartboardId,
                                           ThrowBatchItemDTO item, long participantId) {
        DartSector settore = DartSector.valueOf(item.settore());
        Multiplier moltiplicatore = Multiplier.valueOf(item.moltiplicatore());
        Instant timestamp = Instant.parse(item.timestampSensore());
        int puntiGrezzi = ScoreCalculator.punti(settore, moltiplicatore).orElse(0);
        return throwDao.insertIgnorandoDuplicati(connection, matchId, participantId, "TIRO", settore, moltiplicatore,
                null, null, puntiGrezzi, 0, Esito.IGNORATO, timestamp, Instant.now(), ThrowOrigin.OFFLINE, dartboardId);
    }

    /**
     * Ricostruisce lo stato della partita dai tiri disponibili.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param match partita da elaborare
     * @param regola regola di punteggio da applicare
     * @param chiudiSempre indicatore di chiusura indipendente dal risultato
     * @see java.sql.Connection
     *  
     */
    private void ricalcolaDaZero(Connection connection, Match match, ScoringRule regola, boolean chiudiSempre) {
        long matchId = match.id();
        List<MatchParticipant> partecipanti = participantDao.findByMatch(connection, matchId);
        List<Throw> tutti = throwDao.findAllByMatch(connection, matchId);

        Map<Long, Integer> punteggi = new HashMap<>();
        Map<Long, Integer> numeroTurno = new HashMap<>();
        Map<Long, Integer> tiriNelTurno = new HashMap<>();
        Map<Long, Integer> puntiInizioTurno = new HashMap<>();
        for (MatchParticipant p : partecipanti) {
            punteggi.put(p.id(), regola.punteggioIniziale());
            numeroTurno.put(p.id(), 1);
            tiriNelTurno.put(p.id(), 0);
            puntiInizioTurno.put(p.id(), regola.punteggioIniziale());
        }

        Long vincitoreId = null;
        for (Throw tiro : tutti) {
            long pid = tiro.participantId();
            if (!punteggi.containsKey(pid)) {
                LOG.warn("Tiro {} riferisce un partecipante {} non presente nella partita {}: ignorato nel ricalcolo",
                        tiro.id(), pid, matchId);
                continue;
            }
            int turnoCorrente = numeroTurno.get(pid);
            ThrowOutcome esito = DartsRuleEngine.valuta(regola, punteggi.get(pid), puntiInizioTurno.get(pid),
                    tiriNelTurno.get(pid), tiro.settore(), tiro.moltiplicatore());

            throwDao.aggiornaEsitoRicalcolato(connection, tiro.id(), turnoCorrente, esito.esito(), esito.turnoAnnullato());
            punteggi.put(pid, esito.punteggioResiduo());

            if (esito.esito() == Esito.VALIDO) {
                int fatti = tiriNelTurno.get(pid) + 1;
                if (fatti >= regola.maxTiriPerTurno()) {
                    numeroTurno.put(pid, turnoCorrente + 1);
                    tiriNelTurno.put(pid, 0);
                    puntiInizioTurno.put(pid, esito.punteggioResiduo());
                } else {
                    tiriNelTurno.put(pid, fatti);
                }
            } else if (esito.esito() == Esito.BUST || esito.esito() == Esito.VITTORIA) {
                numeroTurno.put(pid, turnoCorrente + 1);
                tiriNelTurno.put(pid, 0);
                puntiInizioTurno.put(pid, esito.punteggioResiduo());
                if (esito.esito() == Esito.VITTORIA) {
                    vincitoreId = pid;
                }
            }
        }

        for (MatchParticipant p : partecipanti) {
            int nuovoPunteggio = punteggi.get(p.id());
            if (nuovoPunteggio != p.punteggioCorrente()) {
                participantDao.update(connection, new MatchParticipant(p.id(), p.matchId(), p.playerId(), p.teamId(),
                        p.ordineTurno(), nuovoPunteggio, p.ritirato()));
            }
        }

        if ((vincitoreId != null || chiudiSempre) && !matchResultDao.existsForMatch(connection, matchId)) {
            List<MatchParticipant> attivi = partecipanti.stream().filter(p -> !p.ritirato()).toList();
            matchResultService.chiudiSuConnessione(connection, match, attivi, regola, vincitoreId);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param match partita da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    private MatchDTO toDto(Match match) {
        return new MatchDTO(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                match.scoringRuleId(), match.modalita().name(), match.stato().name(), match.createdBy(),
                match.localUuid(), match.clockOffsetMs(),
                match.dataInizio() == null ? null : match.dataInizio().toString(),
                match.dataFine() == null ? null : match.dataFine().toString(), false);
    }
}
