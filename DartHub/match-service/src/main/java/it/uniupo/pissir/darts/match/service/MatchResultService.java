package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.dto.EdgeCommandDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Risultato e posizioni finali di una partita.
 *
 * @see it.uniupo.pissir.darts.match.dao.MatchResultDao
 *  
 */
public final class MatchResultService {

    private final MatchResultDao matchResultDao;
    private final MatchDao matchDao;
    private final DartboardStatusClient dartboardStatusClient;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final MatchEventPublisher matchEventPublisher;

    /**
     * Inizializza {@code MatchResultService} con i dati e le dipendenze indicati.
     *
     * @param matchResultDao componente {@link it.uniupo.pissir.darts.match.dao.MatchResultDao} utilizzato dall'operazione
     * @param matchDao componente {@link it.uniupo.pissir.darts.match.dao.MatchDao} utilizzato dall'operazione
     * @param dartboardStatusClient componente {@link it.uniupo.pissir.darts.match.service.DartboardStatusClient} utilizzato dall'operazione
     * @param edgeCommandPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher} utilizzato dall'operazione
     * @param matchEventPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.match.dao.MatchResultDao
     *  
     */
    public MatchResultService(MatchResultDao matchResultDao, MatchDao matchDao, DartboardStatusClient dartboardStatusClient,
                               EdgeCommandPublisher edgeCommandPublisher, MatchEventPublisher matchEventPublisher) {
        this.matchResultDao = matchResultDao;
        this.matchDao = matchDao;
        this.dartboardStatusClient = dartboardStatusClient;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.matchEventPublisher = matchEventPublisher;
    }

    /**
     * Chiude su connessione per il risultato della partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param match partita da elaborare
     * @param partecipanti partecipanti associati alla partita
     * @param regola regola di punteggio da applicare
     * @param vincitoreParticipantId identificativo del partecipante vincitore
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchResult> chiudiSuConnessione(Connection connection, Match match, List<MatchParticipant> partecipanti,
                                                  ScoringRule regola, Long vincitoreParticipantId) {
        List<WinnerResolver.Piazzamento> piazzamenti = WinnerResolver.risolvi(partecipanti, regola, vincitoreParticipantId);
        List<MatchResult> risultati = new ArrayList<>();
        for (WinnerResolver.Piazzamento piazzamento : piazzamenti) {
            long id = matchResultDao.insert(connection, match.id(), piazzamento.participantId(),
                    piazzamento.punteggioFinale(), piazzamento.posizione(), piazzamento.vincitore());
            risultati.add(new MatchResult(id, match.id(), piazzamento.participantId(), piazzamento.punteggioFinale(),
                    piazzamento.posizione(), piazzamento.vincitore()));
        }

        Match aggiornato = new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                match.scoringRuleId(), match.modalita(), MatchStatus.COMPLETATA, match.createdBy(), match.localUuid(),
                match.clockOffsetMs(), match.dataInizio(), Instant.now());
        matchDao.update(connection, aggiornato);
        return risultati;
    }

    /**
     * Chiude per assenza su connessione per il risultato della partita.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param match partita da elaborare
     * @param participants insieme dei dati relativi a partecipanti
     * @param winnerPlayerId identificativo del giocatore vincitore
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<MatchResult> chiudiPerAssenzaSuConnessione(Connection connection, Match match,
                                                           List<MatchParticipant> participants, Long winnerPlayerId) {
        List<MatchResult> results = new ArrayList<>();
        for (MatchParticipant participant : participants) {
            boolean winner = winnerPlayerId != null && winnerPlayerId.equals(participant.playerId());
            int position = winner ? 1 : winnerPlayerId == null ? 1 : 2;
            long id = matchResultDao.insert(connection, match.id(), participant.id(),
                    participant.punteggioCorrente(), position, winner);
            results.add(new MatchResult(id, match.id(), participant.id(), participant.punteggioCorrente(), position, winner));
        }
        try (java.sql.PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO match_absences(match_id,winner_player_id,type,resolved_at) VALUES(?,?,?,?)")) {
            ps.setLong(1, match.id()); ps.setObject(2, winnerPlayerId);
            ps.setString(3, winnerPlayerId == null ? "DOPPIA_ASSENZA" : "VITTORIA_A_TAVOLINO");
            ps.setString(4, Instant.now().toString()); ps.executeUpdate();
        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        matchDao.update(connection, new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                match.scoringRuleId(), match.modalita(), MatchStatus.COMPLETATA, match.createdBy(), match.localUuid(),
                match.clockOffsetMs(), match.dataInizio(), Instant.now()));
        return results;
    }

    /**
     * Pubblica la chiusura della partita.
     *
     * @param match partita da elaborare
     * @param partecipanti partecipanti associati alla partita
     * @param risultati risultati dei partecipanti alla partita
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    public void pubblicaChiusura(Match match, List<MatchParticipant> partecipanti, List<MatchResult> risultati) {
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:")
                || matchDao.isSensorStarted(match.id())) {
            edgeCommandPublisher.publishCommand(match.localeId(), match.dartboardId(), EdgeCommandDTO.endMatch(match.id()));
            dartboardStatusClient.setDisponibile(match.dartboardId());
        }
        matchEventPublisher.publishMatchCompleted(costruisciEventoCompletata(match, partecipanti, risultati));
    }

    /**
     * Costruisce evento completata per il risultato della partita.
     *
     * @param match partita da elaborare
     * @param partecipanti partecipanti associati alla partita
     * @param risultati risultati dei partecipanti alla partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    private MatchCompletedEventDTO costruisciEventoCompletata(Match match, List<MatchParticipant> partecipanti,
                                                                List<MatchResult> risultati) {
        Map<Long, MatchParticipant> partecipantiPerId = partecipanti.stream()
                .collect(Collectors.toMap(MatchParticipant::id, p -> p));
        List<MatchCompletedEventDTO.Risultato> lista = risultati.stream()
                .map(r -> {
                    MatchParticipant partecipante = partecipantiPerId.get(r.participantId());
                    return new MatchCompletedEventDTO.Risultato(
                            partecipante == null ? null : partecipante.playerId(),
                            partecipante == null ? null : partecipante.teamId(),
                            r.punteggioFinale(), r.posizione(), r.vincitore());
                })
                .toList();
        return new MatchCompletedEventDTO("match-completed-" + match.id(), match.id(), match.gameTypeId(),
                match.localeId(), match.dartboardId(), match.modalita().name(), lista);
    }

    /**
     * Compone l'evento del risultato conclusivo della partita.
     *
     * @param match partita da elaborare
     * @param participants insieme dei dati relativi a partecipanti
     * @param results insieme dei dati relativi a risultati
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public MatchCompletedEventDTO completedEvent(Match match, List<MatchParticipant> participants,
                                                  List<MatchResult> results) {
        return costruisciEventoCompletata(match, participants, results);
    }

    /**
     * Ripubblica solo evento per il risultato della partita.
     *
     * @param match partita da elaborare
     * @param participants insieme dei dati relativi a partecipanti
     * @param results insieme dei dati relativi a risultati
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    public void ripubblicaSoloEvento(Match match, List<MatchParticipant> participants, List<MatchResult> results) {
        matchEventPublisher.publishMatchCompleted(costruisciEventoCompletata(match, participants, results));
    }
}
