package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.tournament.dao.ProcessedMatchDao;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Standing;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.mqtt.TournamentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggiorna la classifica di un torneo a ogni eventi/partite/completata. 
 */
public final class StandingsService {

    private static final Logger LOG = LoggerFactory.getLogger(StandingsService.class);

    private final ConnectionProvider connectionProvider;
    private final TournamentDao tournamentDao;
    private final TournamentMatchDao tournamentMatchDao;
    private final StandingDao standingDao;
    private final ProcessedEventDao processedEventDao;
    private final ProcessedMatchDao processedMatchDao;
    private final ScheduleGenerator scheduleGenerator;
    private final TournamentEventPublisher tournamentEventPublisher;

    /**
     * Inizializza {@code StandingsService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param tournamentDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentDao} utilizzato dall'operazione
     * @param tournamentMatchDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao} utilizzato dall'operazione
     * @param standingDao componente {@link it.uniupo.pissir.darts.tournament.dao.StandingDao} utilizzato dall'operazione
     * @param processedEventDao componente {@link it.uniupo.pissir.darts.common.persistence.ProcessedEventDao} utilizzato dall'operazione
     * @param scheduleGenerator componente {@link it.uniupo.pissir.darts.tournament.service.ScheduleGenerator} utilizzato dall'operazione
     * @param tournamentEventPublisher componente {@link it.uniupo.pissir.darts.tournament.mqtt.TournamentEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public StandingsService(ConnectionProvider connectionProvider, TournamentDao tournamentDao,
                             TournamentMatchDao tournamentMatchDao, StandingDao standingDao,
                             ProcessedEventDao processedEventDao, ScheduleGenerator scheduleGenerator,
                             TournamentEventPublisher tournamentEventPublisher) {
        this(connectionProvider, tournamentDao, tournamentMatchDao, standingDao, processedEventDao,
                new ProcessedMatchDao(), scheduleGenerator, tournamentEventPublisher);
    }

    /**
     * Inizializza {@code StandingsService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param tournamentDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentDao} utilizzato dall'operazione
     * @param tournamentMatchDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao} utilizzato dall'operazione
     * @param standingDao componente {@link it.uniupo.pissir.darts.tournament.dao.StandingDao} utilizzato dall'operazione
     * @param processedEventDao componente {@link it.uniupo.pissir.darts.common.persistence.ProcessedEventDao} utilizzato dall'operazione
     * @param processedMatchDao componente {@link it.uniupo.pissir.darts.tournament.dao.ProcessedMatchDao} utilizzato dall'operazione
     * @param scheduleGenerator componente {@link it.uniupo.pissir.darts.tournament.service.ScheduleGenerator} utilizzato dall'operazione
     * @param tournamentEventPublisher componente {@link it.uniupo.pissir.darts.tournament.mqtt.TournamentEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public StandingsService(ConnectionProvider connectionProvider, TournamentDao tournamentDao,
                             TournamentMatchDao tournamentMatchDao, StandingDao standingDao,
                             ProcessedEventDao processedEventDao, ProcessedMatchDao processedMatchDao,
                             ScheduleGenerator scheduleGenerator, TournamentEventPublisher tournamentEventPublisher) {
        this.connectionProvider = connectionProvider;
        this.tournamentDao = tournamentDao;
        this.tournamentMatchDao = tournamentMatchDao;
        this.standingDao = standingDao;
        this.processedEventDao = processedEventDao;
        this.processedMatchDao = processedMatchDao;
        this.scheduleGenerator = scheduleGenerator;
        this.tournamentEventPublisher = tournamentEventPublisher;
    }

    /**
     * Elenca gli esiti previsti per l'elaborazione di un tiro.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param tournamentId identificativo del torneo
     * @param classifica classifica dei partecipanti
     * @see java.util.List
     *  
     */
    private record Esito(long tournamentId, List<Standing> classifica) {
    }

    /**
     * Gestisce completamento per la classifica.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public void gestisciCompletamento(MatchCompletedEventDTO evento) {
        Optional<Esito> esito = connectionProvider.withTransaction(connection -> elabora(connection, evento));
        esito.ifPresent(e -> tournamentEventPublisher.publish(new TournamentUpdatedEventDTO(
                UUID.randomUUID().toString(), e.tournamentId(), e.classifica().stream()
                .map(s -> new TournamentUpdatedEventDTO.Standing(s.participantId(), s.punti(), s.partiteGiocate(),
                        s.vittorie(), s.posizione()))
                .toList())));
    }

    /**
     * Elabora il risultato e aggiorna lo stato del torneo.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param evento evento da elaborare
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @throws IllegalStateException se viene rilevato l'errore «Torneo inesistente per tournament_matches:»
     * @see java.util.Optional
     *  
     */
    private Optional<Esito> elabora(Connection connection, MatchCompletedEventDTO evento) {
        if (processedEventDao.giaElaborato(connection, evento.eventId())) {
            LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
            return Optional.empty();
        }
        Optional<TournamentMatch> tm = tournamentMatchDao.findByMatch(evento.matchId());
        if (tm.isEmpty()) {
            processedEventDao.segnaElaborato(connection, evento.eventId());
            return Optional.empty();
        }
        if (processedMatchDao.exists(connection, evento.matchId())) return Optional.empty();
        Tournament tournament = tournamentDao.findById(tm.get().tournamentId())
                .orElseThrow(() -> new IllegalStateException("Torneo inesistente per tournament_matches: " + tm.get().tournamentId()));

        Long vincitoreParticipantId = null;
        for (MatchCompletedEventDTO.Risultato r : evento.risultati()) {
            Long participantId = tournament.modalita() == ModalitaTorneo.INDIVIDUALE ? r.playerId() : r.teamId();
            if (participantId == null) {
                continue;
            }
            int puntiDelta = r.vincitore() ? tournament.puntiVittoria() : tournament.puntiSconfitta();
            standingDao.applicaRisultato(connection, tournament.id(), participantId, puntiDelta, r.vincitore());
            if (r.vincitore()) {
                vincitoreParticipantId = participantId;
            }
        }
        List<Standing> classifica = ricalcolaPosizioni(connection, tournament.id());
        processedEventDao.segnaElaborato(connection, evento.eventId());
        processedMatchDao.mark(connection, evento.matchId());

        if (tournament.formula() == it.uniupo.pissir.darts.tournament.model.FormulaTorneo.ELIMINAZIONE
                && vincitoreParticipantId != null) {
            scheduleGenerator.avanzaEliminazione(connection, tournament, tm.get().turno(), tm.get().slot(), vincitoreParticipantId);
        } else if (tournament.formula() == it.uniupo.pissir.darts.tournament.model.FormulaTorneo.ELIMINAZIONE
                && tm.get().slot() != null) {
            scheduleGenerator.avanzaEliminazioneSenzaVincitore(connection, tournament, tm.get().turno(),
                    tm.get().slot(), evento.matchId());
        }
        return Optional.of(new Esito(tournament.id(), classifica));
    }

    /**
     * Ricalcola posizioni per la classifica.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private List<Standing> ricalcolaPosizioni(Connection connection, long tournamentId) {
        List<Standing> classifica = standingDao.findByTournament(connection, tournamentId);
        for (int i = 0; i < classifica.size(); i++) {
            standingDao.aggiornaPosizione(connection, tournamentId, classifica.get(i).participantId(), i + 1);
        }
        return classifica;
    }
}
