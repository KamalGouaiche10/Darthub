package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.dao.PairingNoticeDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.dto.PairingDTO;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Applica le regole e coordina le operazioni relative a abbinamenti.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
 *  
 */
public final class PairingService {
    private final TournamentDao tournaments;
    private final TournamentMatchDao matches;
    private final EnrollmentDao enrollments;
    private final PairingNoticeDao notices;
    private final UserClient users;
    private final MatchServiceClient teams;
    private final LocaleClient locales;
    private final MatchAppointmentService appointments;

    /**
     * Inizializza {@code PairingService} con i dati e le dipendenze indicati.
     *
     * @param tournaments tornei da utilizzare
     * @param matches partite da utilizzare
     * @param enrollments iscrizioni da utilizzare
     * @param notices notices da utilizzare
     * @param users utenti da utilizzare
     * @param teams squadre da utilizzare
     * @param locales locales da utilizzare
     * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
     *  
     */
    public PairingService(TournamentDao tournaments, TournamentMatchDao matches, EnrollmentDao enrollments,
                          PairingNoticeDao notices, UserClient users, MatchServiceClient teams, LocaleClient locales) {
        this(tournaments, matches, enrollments, notices, users, teams, locales, null);
    }

    /**
     * Inizializza {@code PairingService} con i dati e le dipendenze indicati.
     *
     * @param tournaments tornei da utilizzare
     * @param matches partite da utilizzare
     * @param enrollments iscrizioni da utilizzare
     * @param notices notices da utilizzare
     * @param users utenti da utilizzare
     * @param teams squadre da utilizzare
     * @param locales locales da utilizzare
     * @param appointments appointments da utilizzare
     * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
     *  
     */
    public PairingService(TournamentDao tournaments, TournamentMatchDao matches, EnrollmentDao enrollments,
                          PairingNoticeDao notices, UserClient users, MatchServiceClient teams, LocaleClient locales,
                          MatchAppointmentService appointments) {
        this.tournaments = tournaments;
        this.matches = matches;
        this.enrollments = enrollments;
        this.notices = notices;
        this.users = users;
        this.teams = teams;
        this.locales = locales;
        this.appointments = appointments;
    }

    /**
     * Compone i dati del partecipante per il giocatore indicato.
     *
     * @param playerId identificativo del giocatore
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<PairingDTO> forPlayer(long playerId) {
        List<PairingDTO> result = new ArrayList<>();
        int page = 0;
        List<Tournament> batch;
        do {
            batch = tournaments.findAll(new Page(page++, 100));
            for (Tournament tournament : batch) {
                if (tournament.stato() == TournamentStatus.APERTO) continue;
                for (TournamentMatch match : matches.findByTournament(tournament.id())) {
                    Long home = match.homeParticipantId();
                    Long away = match.awayParticipantId();
                    if (home == null || away == null) continue;
                    boolean isHome = belongs(tournament, home, playerId);
                    boolean isAway = belongs(tournament, away, playerId);
                    if (!isHome && !isAway) continue;
                    String homeName = name(tournament, home);
                    String awayName = name(tournament, away);
                    MatchAppointmentService.View appointment = null;
                    if (appointments != null) {
                        try { appointment = appointments.view(match.matchId()); }
                        catch (it.uniupo.pissir.darts.common.exception.NotFoundException ignored) { }
                    }
                    var acceptance = appointments == null ? null : appointments.acceptance(match.matchId());
                    result.add(new PairingDTO(match.matchId(), tournament.id(), tournament.nome(), match.turno(),
                            match.localeId() == null ? "Locale da definire" : locales.nome(match.localeId()),
                            homeName + " contro " + awayName, isHome ? awayName : homeName, false,
                            notices.isRead(playerId, match.matchId()), appointment == null ? null : appointment.scheduledAt(),
                            appointment == null ? null : appointment.linkAt(), appointment == null ? null : appointment.deadline(),
                            appointment == null ? null : appointment.outcome(), appointment == null ? null : appointment.matchStatus(),
                            appointment != null && appointment.homeReady(), appointment != null && appointment.awayReady(),
                            appointment != null && tournament.modalita() == ModalitaTorneo.INDIVIDUALE
                                    && appointment.linkAvailable(),
                            appointment == null ? null : "DOPPIA_ASSENZA".equals(appointment.outcome())
                                    ? "Doppia assenza: nessun vincitore"
                                    : "VITTORIA_A_TAVOLINO".equals(appointment.outcome())
                                    ? "Vittoria a tavolino di " + (appointment.homeReady() ? homeName : awayName)
                                    + "; sconfitta per assenza di " + (appointment.homeReady() ? awayName : homeName)
                                    : null, acceptance != null && acceptance.home(),
                            acceptance != null && acceptance.away(), tournament.modalita() == ModalitaTorneo.INDIVIDUALE,
                            acceptance != null && (isHome ? acceptance.home() : acceptance.away())));
                }
                addByes(result, tournament, playerId);
            }
        } while (batch.size() == 100);
        return result;
    }

    /**
     * Registra lo stato lettura.
     *
     * @param playerId identificativo del giocatore
     * @param matchId identificativo della partita
     * @throws NotFoundException se viene rilevato l'errore «Abbinamento non trovato per il giocatore»
     *  
     */
    public void markRead(long playerId, long matchId) {
        if (forPlayer(playerId).stream().noneMatch(p -> p.matchId() == matchId)) {
            throw new NotFoundException("Abbinamento non trovato per il giocatore");
        }
        notices.markRead(playerId, matchId);
    }

    /**
     * Verifica l'appartenenza del partecipante al torneo.
     *
     * @param tournament torneo da elaborare
     * @param participantId identificativo del partecipante
     * @param playerId identificativo del giocatore
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private boolean belongs(Tournament tournament, long participantId, long playerId) {
        return tournament.modalita() == ModalitaTorneo.INDIVIDUALE
                ? participantId == playerId : teams.teamContains(participantId, playerId);
    }

    /**
     * Recupera il nome del partecipante o della risorsa indicata.
     *
     * @param tournament torneo da elaborare
     * @param participantId identificativo del partecipante
     * @return il testo o il valore di configurazione richiesto
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private String name(Tournament tournament, long participantId) {
        return tournament.modalita() == ModalitaTorneo.INDIVIDUALE
                ? users.displayName(participantId) : teams.teamName(participantId);
    }

    /**
     * Inserisce nel tabellone i passaggi del turno senza avversario.
     *
     * @param result insieme dei dati relativi a risultato
     * @param tournament torneo da elaborare
     * @param playerId identificativo del giocatore
     * @see java.util.List
     *  
     */
    private void addByes(List<PairingDTO> result, Tournament tournament, long playerId) {
        List<Long> ids = enrollments.findByTournament(tournament.id()).stream().map(e -> e.participantId()).toList();
        if (ids.size() < 2) return;
        if (tournament.formula() == FormulaTorneo.GIRONE && ids.size() % 2 == 1) {
            List<Long> rotating = new ArrayList<>(ids);
            rotating.add(null);
            for (int round = 1; round < rotating.size(); round++) {
                int n = rotating.size();
                for (int i = 0; i < n / 2; i++) {
                    Long a = rotating.get(i), b = rotating.get(n - 1 - i);
                    Long bye = a == null ? b : b == null ? a : null;
                    if (bye != null && belongs(tournament, bye, playerId)) addBye(result, tournament, playerId, round, bye);
                }
                Long last = rotating.remove(rotating.size() - 1);
                rotating.add(1, last);
            }
        } else if (tournament.formula() == FormulaTorneo.ELIMINAZIONE) {
            int slots = 1;
            while (slots < ids.size()) slots *= 2;
            for (int i = 0; i < slots - ids.size(); i++) {
                long bye = ids.get(i);
                if (belongs(tournament, bye, playerId)) addBye(result, tournament, playerId, 1, bye);
            }
        }
    }

    /**
     * Inserisce un passaggio del turno senza avversario.
     *
     * @param result insieme dei dati relativi a risultato
     * @param tournament torneo da elaborare
     * @param playerId identificativo del giocatore
     * @param round numero del turno del torneo
     * @param participantId identificativo del partecipante
     * @see java.util.List
     *  
     */
    private void addBye(List<PairingDTO> result, Tournament tournament, long playerId, int round, long participantId) {
        long key = -(tournament.id() * 1000 + round);
        result.add(new PairingDTO(key, tournament.id(), tournament.nome(), round, "Nessun locale necessario",
                name(tournament, participantId) + " senza avversario (riposo)", "Riposo", true,
                notices.isRead(playerId, key)));
    }
}
