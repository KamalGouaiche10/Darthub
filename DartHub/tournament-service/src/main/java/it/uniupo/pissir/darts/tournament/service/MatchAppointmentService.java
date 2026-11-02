package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Applica le regole e coordina le operazioni relative a appuntamenti delle partite.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao
 *  
 */
public final class MatchAppointmentService {
    /**
     * Raccoglie i dati di view.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param matchId identificativo della partita
     * @param scheduledAt istante programmato per l'inizio della partita
     * @param linkAt istante da cui il collegamento alla partita Ã¨ disponibile
     * @param deadline istante limite per la conferma della presenza
     * @param durationMinutes durata della partita in minuti
     * @param homeReady indicatore di presenza del partecipante di casa
     * @param awayReady indicatore di presenza del partecipante ospite
     * @param outcome esito della partita
     * @param matchStatus stato corrente della partita
     * @param linkAvailable indicatore di disponibilitÃ  del collegamento alla partita
     *  
     */
    public record View(long matchId, String scheduledAt, String linkAt, String deadline,
                       int durationMinutes, boolean homeReady, boolean awayReady, String outcome,
                       String matchStatus, boolean linkAvailable) { }

    private final MatchAppointmentDao appointments;
    private final TournamentMatchDao matches;
    private final TournamentService tournaments;
    private final MatchServiceClient matchClient;
    private final Clock clock;
    private final int graceMinutes;
    private StandingsService standings;

    /**
     * Inizializza {@code MatchAppointmentService} con i dati e le dipendenze indicati.
     *
     * @param appointments appointments da utilizzare
     * @param matches partite da utilizzare
     * @param tournaments tornei da utilizzare
     * @param matchClient componente {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient} utilizzato dall'operazione
     * @param clock orologio usato dall'operazione
     * @param graceMinutes ritardo ammesso in minuti
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao
     *  
     */
    public MatchAppointmentService(MatchAppointmentDao appointments, TournamentMatchDao matches,
                                   TournamentService tournaments, MatchServiceClient matchClient,
                                   Clock clock, int graceMinutes) {
        this.appointments = appointments; this.matches = matches; this.tournaments = tournaments;
        this.matchClient = matchClient; this.clock = clock; this.graceMinutes = graceMinutes;
    }

    /**
     * Imposta standings servizio per il componente.
     *
     * @param standings standings da utilizzare
     * @see it.uniupo.pissir.darts.tournament.service.StandingsService
     *  
     */
    public void setStandingsService(StandingsService standings) { this.standings = standings; }

    /**
     * Raccoglie i dati di acceptance view.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param homeAccepted indicatore di accettazione del partecipante di casa
     * @param awayAccepted indicatore di accettazione del partecipante ospite
     * @param appointment appuntamento della partita
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public record AcceptanceView(boolean homeAccepted, boolean awayAccepted, View appointment) { }

    /**
     * Registra l'accettazione dell'appuntamento da parte del giocatore.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.AcceptanceView} prodotto o recuperato
     * @throws ForbiddenException se viene rilevato l'errore «Non sei un partecipante di questa partita»
     * @throws ConflictException se viene rilevato l'errore «La partita non può più essere accettata»
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.AcceptanceView
     *  
     */
    public synchronized AcceptanceView accept(long matchId, long playerId) {
        TournamentMatch match = match(matchId);
        Tournament tournament = tournaments.requireTournament(match.tournamentId());
        if (tournament.modalita() != ModalitaTorneo.INDIVIDUALE
                || match.homeParticipantId() == null || match.awayParticipantId() == null
                || (match.homeParticipantId() != playerId && match.awayParticipantId() != playerId))
            throw new ForbiddenException("Non sei un partecipante di questa partita");
        if (!"CREATA".equals(matchClient.statoOf(matchId)))
            throw new ConflictException("PARTITA_NON_APERTA", "La partita non può più essere accettata");
        MatchAppointmentDao.Acceptance accepted = appointments.accept(matchId,
                playerId == match.homeParticipantId(), clock.instant());
        if (accepted.both() && appointments.findOrNull(matchId) == null) {
            Instant earliest = clock.instant().plusSeconds(60);
            Instant lower = earliest.truncatedTo(ChronoUnit.SECONDS);
            if (lower.isBefore(earliest)) lower = lower.plusSeconds(1);
            if (lower.isBefore(tournament.dataInizio())) lower = tournament.dataInizio();
            int remainingRounds = 0;
            if (tournament.formula() == it.uniupo.pissir.darts.tournament.model.FormulaTorneo.ELIMINAZIONE) {
                long enrolled = tournaments.enrollmentCount(tournament.id());
                int slots = 1;
                while (slots < enrolled) slots *= 2;
                int rounds = Integer.numberOfTrailingZeros(slots);
                remainingRounds = Math.max(0, rounds - match.turno());
            }
            Instant upper = tournament.dataFine() == null ? null
                    : tournament.dataFine().minusSeconds(3600L + remainingRounds * 3960L);
            if (upper == null || upper.isBefore(lower))
                throw new ConflictException("INTERVALLO_ESAURITO", "Non resta un'ora libera per la partita prima della fine del torneo");
            long dartboard = matchClient.dartboardIdOf(matchId);
            appointments.scheduleFirstAvailable(tournament.id(), matchId, dartboard, lower, upper, 60);
        }
        MatchAppointmentDao.Appointment a = appointments.findOrNull(matchId);
        return new AcceptanceView(accepted.home(), accepted.away(), a == null ? null : view(a));
    }

    /**
     * Compone lo stato di accettazione dell'appuntamento.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance
     *  
     */
    public MatchAppointmentDao.Acceptance acceptance(long matchId) { return appointments.acceptance(matchId); }

    /**
     * Pianifica l'appuntamento della partita.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param at istante dell'operazione
     * @param durationMinutes durata della partita in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «L'orario assegnato ai giocatori non può essere modificato»
     * @throws NotFoundException se viene rilevato l'errore «Partita non appartenente al torneo»
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public synchronized View schedule(long tournamentId, long matchId, Instant at, int durationMinutes) {
        if (appointments.acceptance(matchId).both())
            throw new ConflictException("ORARIO_GIA_ASSEGNATO", "L'orario assegnato ai giocatori non può essere modificato");
        Tournament tournament = tournaments.requireTournament(tournamentId);
        if (tournament.modalita() != ModalitaTorneo.INDIVIDUALE)
            throw new ConflictException("SOLO_INDIVIDUALE", "Gli orari con presenza individuale sono disponibili solo per tornei individuali");
        TournamentMatch match = matches.findByTournamentAndMatch(tournamentId, matchId)
                .orElseThrow(() -> new NotFoundException("Partita non appartenente al torneo"));
        if (match.homeParticipantId() == null || match.awayParticipantId() == null)
            throw new ConflictException("ABBINAMENTO_INCOMPLETO", "Abbinamento incompleto");
        if (!at.isAfter(clock.instant())) throw new ConflictException("ORARIO_PASSATO", "Scegli un orario futuro");
        if (tournament.dataFine() != null && (at.isBefore(tournament.dataInizio())
                || at.plusSeconds(durationMinutes * 60L).isAfter(tournament.dataFine())))
            throw new ConflictException("ORARIO_FUORI_TORNEO", "L'orario deve rientrare nelle date del torneo");
        if (!"CREATA".equals(matchClient.statoOf(matchId)))
            throw new ConflictException("PARTITA_GIA_AVVIATA", "La partita è già avviata");
        return view(appointments.schedule(tournamentId, matchId, matchClient.dartboardIdOf(matchId), at, durationMinutes));
    }

    /**
     * Legge le partite associate al torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<TournamentMatch> matches(long tournamentId) {
        tournaments.requireTournament(tournamentId);
        return matches.findByTournament(tournamentId);
    }

    /**
     * Compone i dati dell'appuntamento per la risposta.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public View view(long matchId) { return view(appointments.find(matchId)); }

    /**
     * Compone i dati dell'appuntamento visibili al giocatore.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public View viewForPlayer(long matchId, long playerId) {
        MatchAppointmentDao.Appointment a = appointments.find(matchId);
        requirePlayer(a, match(matchId), playerId);
        return view(a);
    }

    /**
     * Registra la presenza del giocatore all'appuntamento.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita è già avviata o conclusa»
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public synchronized View ready(long matchId, long playerId) {
        MatchAppointmentDao.Appointment a = appointments.find(matchId);
        TournamentMatch match = match(matchId);
        requirePlayer(a, match, playerId);
        if (!"CREATA".equals(matchClient.statoOf(matchId)))
            throw new ConflictException("PARTITA_NON_APERTA", "La partita è già avviata o conclusa");
        return view(appointments.ready(matchId, playerId == match.homeParticipantId(), clock.instant(), graceMinutes));
    }

    /**
     * Verifica l'accesso del giocatore all'appuntamento della partita.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @throws ForbiddenException se viene rilevato l'errore «Il bersaglio non è disponibile»
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    public View access(long matchId, long playerId) {
        MatchAppointmentDao.Appointment a = appointments.find(matchId);
        requirePlayer(a, match(matchId), playerId);
        View v = view(a);
        if (!v.linkAvailable()) throw new ForbiddenException("Il bersaglio non è disponibile");
        return v;
    }

    /**
     * Elabora gli appuntamenti che hanno raggiunto la scadenza. 
     */
    public synchronized void processDue() {
        Instant now = clock.instant();
        for (MatchAppointmentDao.Appointment a : appointments.due(now, graceMinutes)) {
            try {
                if ("VITTORIA_A_TAVOLINO".equals(a.outcome()) || "DOPPIA_ASSENZA".equals(a.outcome())
                        || "COMPLETATA".equals(a.outcome())) {
                    applyAbsenceResult(a.matchId());
                    continue;
                }
                if ("RISOLUZIONE".equals(a.outcome())) {
                    resolveClaimedAbsence(a);
                    continue;
                }
                String matchStatus = matchClient.statoOf(a.matchId());
                if (!"CREATA".equals(matchStatus)) {
                    if ("IN_CORSO".equals(matchStatus) && a.outcome() == null) {
                        matchClient.startTournament(a.matchId());
                        appointments.setOutcome(a.matchId(), "IN_CORSO", a.revision());
                    }
                    if ("COMPLETATA".equals(matchStatus)) {
                        appointments.setOutcome(a.matchId(), "COMPLETATA", a.revision());
                        applyAbsenceResult(a.matchId());
                    }
                    continue;
                }
                if (a.homeReadyAt() != null && a.awayReadyAt() != null) {
                    matchClient.startTournament(a.matchId());
                    appointments.setOutcome(a.matchId(), "IN_CORSO", a.revision());
                } else if (!now.isBefore(a.deadline(graceMinutes))) {
                    MatchAppointmentDao.Appointment claimed = appointments.claimAbsence(a.matchId(), now, graceMinutes);
                    if (claimed != null) resolveClaimedAbsence(claimed);
                }
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scadenza partita {}: {}", a.matchId(), e.getMessage());
            }
        }
    }

    /**
     * Completa la risoluzione per assenza già prenotata.
     *
     * @param a dato {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} da elaborare
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    private void resolveClaimedAbsence(MatchAppointmentDao.Appointment a) {
        TournamentMatch match = match(a.matchId());
        Long winner = a.homeReadyAt() != null ? match.homeParticipantId()
                : a.awayReadyAt() != null ? match.awayParticipantId() : null;
        matchClient.resolveAbsence(a.matchId(), winner);
        appointments.setOutcome(a.matchId(), winner == null ? "DOPPIA_ASSENZA" : "VITTORIA_A_TAVOLINO", a.revision());
        applyAbsenceResult(a.matchId());
    }

    /**
     * Applica alla partita il risultato determinato dalle assenze.
     *
     * @param matchId identificativo della partita
     *  
     */
    private void applyAbsenceResult(long matchId) {
        if (standings != null) standings.gestisciCompletamento(matchClient.completedEvent(matchId));
    }

    /**
     * Recupera la partita indicata dal servizio remoto.
     *
     * @param id identificativo dell'appuntamento della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.TournamentMatch} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Partita di torneo non trovata»
     * @see it.uniupo.pissir.darts.tournament.model.TournamentMatch
     *  
     */
    private TournamentMatch match(long id) {
        return matches.findByMatch(id).orElseThrow(() -> new NotFoundException("Partita di torneo non trovata"));
    }

    /**
     * Richiede che il giocatore esista e sia accessibile.
     *
     * @param a dato {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} da elaborare
     * @param m dato {@link it.uniupo.pissir.darts.tournament.model.TournamentMatch} da elaborare
     * @param playerId identificativo del giocatore
     * @throws ForbiddenException se viene rilevato l'errore «Non sei un partecipante di questa partita»
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    private void requirePlayer(MatchAppointmentDao.Appointment a, TournamentMatch m, long playerId) {
        if (tournaments.requireTournament(a.tournamentId()).modalita() != ModalitaTorneo.INDIVIDUALE
                || m.homeParticipantId() == null || m.awayParticipantId() == null
                || (m.homeParticipantId() != playerId && m.awayParticipantId() != playerId))
            throw new ForbiddenException("Non sei un partecipante di questa partita");
    }

    /**
     * Compone i dati dell'appuntamento per la risposta.
     *
     * @param a dato {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.service.MatchAppointmentService.View
     *  
     */
    private View view(MatchAppointmentDao.Appointment a) {
        Instant now = clock.instant();
        String status = matchClient.statoOf(a.matchId());
        boolean withinCheckIn = !now.isAfter(a.deadline(graceMinutes));
        boolean bothReady = a.homeReadyAt() != null && a.awayReadyAt() != null;
        boolean open = !now.isBefore(a.linkAt()) && (a.outcome() == null || "IN_CORSO".equals(a.outcome()))
                && (("CREATA".equals(status) && (withinCheckIn || bothReady)) || "IN_CORSO".equals(status));
        return new View(a.matchId(), a.scheduledAt().toString(), a.linkAt().toString(),
                a.deadline(graceMinutes).toString(), a.durationMinutes(), a.homeReadyAt() != null,
                a.awayReadyAt() != null, a.outcome(), status, open);
    }
}
