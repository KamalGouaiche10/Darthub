package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.BracketSlotDao;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.BracketSlot;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dagli iscritti e dai locali di un torneo genera le partite, chiamando POST /partite su match-service.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class ScheduleGenerator {

    private final ConnectionProvider connectionProvider;
    private final TournamentLocaleDao tournamentLocaleDao;
    private final TournamentMatchDao tournamentMatchDao;
    private final BracketSlotDao bracketSlotDao;
    private final EnrollmentDao enrollmentDao;
    private final LocaleClient localeClient;
    private final MatchServiceClient matchServiceClient;
    private final TournamentService tournamentService;

    /**
     * Inizializza {@code ScheduleGenerator} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param tournamentLocaleDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao} utilizzato dall'operazione
     * @param tournamentMatchDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao} utilizzato dall'operazione
     * @param bracketSlotDao componente {@link it.uniupo.pissir.darts.tournament.dao.BracketSlotDao} utilizzato dall'operazione
     * @param enrollmentDao componente {@link it.uniupo.pissir.darts.tournament.dao.EnrollmentDao} utilizzato dall'operazione
     * @param localeClient componente {@link it.uniupo.pissir.darts.tournament.service.LocaleClient} utilizzato dall'operazione
     * @param matchServiceClient componente {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient} utilizzato dall'operazione
     * @param tournamentService componente {@link it.uniupo.pissir.darts.tournament.service.TournamentService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public ScheduleGenerator(ConnectionProvider connectionProvider, TournamentLocaleDao tournamentLocaleDao,
                              TournamentMatchDao tournamentMatchDao, BracketSlotDao bracketSlotDao,
                              EnrollmentDao enrollmentDao, LocaleClient localeClient, MatchServiceClient matchServiceClient,
                              TournamentService tournamentService) {
        this.connectionProvider = connectionProvider;
        this.tournamentLocaleDao = tournamentLocaleDao;
        this.tournamentMatchDao = tournamentMatchDao;
        this.bracketSlotDao = bracketSlotDao;
        this.enrollmentDao = enrollmentDao;
        this.localeClient = localeClient;
        this.matchServiceClient = matchServiceClient;
        this.tournamentService = tournamentService;
    }

    /**
     * Genera il calendario delle partite del torneo.
     *
     * @param tournamentId identificativo del torneo
     * @param force indicatore di esecuzione forzata
     * @return gli elementi trovati o prodotti dall'operazione
     * @throws ConflictException se viene rilevato l'errore «Il calendario si genera dalla data di inizio del torneo»
     * @throws BusinessRuleException se viene rilevato l'errore «Servono almeno due iscritti per generare il calendario»
     * @see java.util.List
     *  
     */
    public synchronized List<Long> generaCalendario(long tournamentId, boolean force) {
        Tournament tournament = tournamentService.requireTournament(tournamentId);
        tournamentService.requireModificabile(tournament);
        if (java.time.Instant.now().isBefore(tournament.dataInizio())) {
            throw new ConflictException("TORNEO_NON_INIZIATO", "Il calendario si genera dalla data di inizio del torneo");
        }
        if (tournament.stato() != it.uniupo.pissir.darts.tournament.model.TournamentStatus.APERTO) {
            return tournamentMatchDao.findByTournament(tournamentId).stream().map(m -> m.matchId()).toList();
        }

        List<Enrollment> iscritti = enrollmentDao.findByTournament(tournamentId);
        if (iscritti.size() < 2) {
            throw new BusinessRuleException("ISCRITTI_INSUFFICIENTI", "Servono almeno due iscritti per generare il calendario");
        }
        List<Long> locali = tournamentLocaleDao.findLocaliIds(tournamentId);
        if (locali.isEmpty()) {
            throw new BusinessRuleException("TORNEO_SENZA_LOCALI", "Il torneo non ha nessun locale associato");
        }

        Map<Long, Long> dartboardPerLocale = new HashMap<>();
        for (Long localeId : locali) {
            dartboardPerLocale.put(localeId, localeClient.unBersaglioCompatibile(localeId, tournament.gameTypeId()));
        }

        List<Long> matchIds = tournament.formula() == FormulaTorneo.GIRONE
                ? generaGirone(tournament, iscritti, locali, dartboardPerLocale)
                : generaEliminazionePrimoTurno(tournament, iscritti, locali, dartboardPerLocale);

        tournamentService.segnaInCorso(tournamentId);
        return matchIds;
    }

    /**
     * Genera gli incontri del girone.
     *
     * @param tournament torneo da elaborare
     * @param iscritti insieme dei dati relativi a iscritti
     * @param locali insieme dei locali
     * @param dartboardPerLocale insieme dei dati relativi a bersaglio per locale
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private List<Long> generaGirone(Tournament tournament, List<Enrollment> iscritti, List<Long> locali,
                                     Map<Long, Long> dartboardPerLocale) {
        List<Long> partecipanti = new ArrayList<>(iscritti.stream().map(Enrollment::participantId).toList());
        boolean dispari = partecipanti.size() % 2 != 0;
        if (dispari) {
            partecipanti.add(null);
        }
        int n = partecipanti.size();
        int turni = n - 1;
        int meta = n / 2;
        List<Long> matchIds = new ArrayList<>();
        int indiceLocale = 0;

        for (int turno = 0; turno < turni; turno++) {
            for (int i = 0; i < meta; i++) {
                Long home = partecipanti.get(i);
                Long away = partecipanti.get(n - 1 - i);
                if (home != null && away != null) {
                    long localeId = locali.get(indiceLocale % locali.size());
                    long dartboardId = dartboardPerLocale.get(localeId);
                    long matchId = creaPartitaPerCoppia(tournament, home, away, localeId, dartboardId);
                    tournamentMatchDao.insert(tournament.id(), matchId, turno + 1, null);
                    tournamentMatchDao.setPairing(tournament.id(), matchId, home, away, localeId);
                    matchIds.add(matchId);
                    indiceLocale++;
                }
            }
            ruotaGironeItaliano(partecipanti);
        }
        return matchIds;
    }

    /**
     * Ruota i partecipanti per comporre il turno successivo del girone.
     *
     * @param partecipanti partecipanti associati alla partita
     * @see java.util.List
     *  
     */
    private void ruotaGironeItaliano(List<Long> partecipanti) {
        int n = partecipanti.size();
        Long ultimo = partecipanti.get(n - 1);
        for (int i = n - 1; i > 1; i--) {
            partecipanti.set(i, partecipanti.get(i - 1));
        }
        partecipanti.set(1, ultimo);
    }

    /**
     * Compone gli incontri del primo turno a eliminazione.
     *
     * @param tournament torneo da elaborare
     * @param iscritti insieme dei dati relativi a iscritti
     * @param locali insieme dei locali
     * @param dartboardPerLocale insieme dei dati relativi a bersaglio per locale
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private List<Long> generaEliminazionePrimoTurno(Tournament tournament, List<Enrollment> iscritti,
                                                      List<Long> locali, Map<Long, Long> dartboardPerLocale) {
        List<Long> reali = iscritti.stream().map(Enrollment::participantId).toList();
        int n = reali.size();
        int slots = prossimaPotenzaDiDue(n);
        int numeroBye = slots - n;
        List<Long> matchIds = new ArrayList<>();

        return connectionProvider.withTransaction(connection -> {
            int pairIndex = 0;
            int indiceLocale = 0;
            for (int i = 0; i < numeroBye; i++) {
                long partecipante = reali.get(i);
                risolviContestant(connection, tournament, 2, pairIndex, partecipante, locali, dartboardPerLocale, matchIds);
                pairIndex++;
            }
            for (int i = numeroBye; i < n; i += 2) {
                long home = reali.get(i);
                long away = reali.get(i + 1);
                long localeId = locali.get(indiceLocale % locali.size());
                long dartboardId = dartboardPerLocale.get(localeId);
                long matchId = creaPartitaPerCoppia(tournament, home, away, localeId, dartboardId);
                tournamentMatchDao.insert(connection, tournament.id(), matchId, 1, pairIndex);
                tournamentMatchDao.setPairing(connection, tournament.id(), matchId, home, away, localeId);
                matchIds.add(matchId);
                pairIndex++;
                indiceLocale++;
            }
            return matchIds;
        });
    }

    /**
     * Compone l'incontro successivo del tabellone a eliminazione.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournament torneo da elaborare
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @param vincitoreParticipantId identificativo del partecipante vincitore
     * @see java.sql.Connection
     *  
     */
    public void avanzaEliminazione(Connection connection, Tournament tournament, int turno, int slot,
                                    long vincitoreParticipantId) {
        int n = enrollmentDao.findByTournament(tournament.id()).size();
        int slots = prossimaPotenzaDiDue(n);
        int turniTotali = log2(slots);
        if (turno >= turniTotali) {
            return;
        }
        List<Long> locali = tournamentLocaleDao.findLocaliIds(tournament.id());
        Map<Long, Long> dartboardPerLocale = new HashMap<>();
        for (Long localeId : locali) {
            dartboardPerLocale.put(localeId, localeClient.unBersaglioCompatibile(localeId, tournament.gameTypeId()));
        }
        risolviContestant(connection, tournament, turno + 1, slot, vincitoreParticipantId, locali,
                dartboardPerLocale, new ArrayList<>());
    }

    /**
     * Aggiorna il tabellone quando l'incontro non produce un vincitore.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournament torneo da elaborare
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @param sourceMatchId identificativo della partita di origine
     * @see java.sql.Connection
     *  
     */
    public void avanzaEliminazioneSenzaVincitore(Connection connection, Tournament tournament,
                                                  int turno, int slot, long sourceMatchId) {
        int slots = prossimaPotenzaDiDue(enrollmentDao.findByTournament(tournament.id()).size());
        if (turno >= log2(slots)) return;
        int nextRound = turno + 1;
        bracketSlotDao.upsert(connection, tournament.id(), nextRound, slot, null, -sourceMatchId);
        int sibling = slot ^ 1;
        Optional<BracketSlot> other = bracketSlotDao.find(connection, tournament.id(), nextRound, sibling);
        if (other.isEmpty()) return;
        if (other.get().participantId() != null && other.get().matchId() == null) {
            if (nextRound >= log2(slots)) return;
            List<Long> locali = tournamentLocaleDao.findLocaliIds(tournament.id());
            Map<Long, Long> boards = new HashMap<>();
            for (Long localeId : locali)
                boards.put(localeId, localeClient.unBersaglioCompatibile(localeId, tournament.gameTypeId()));
            risolviContestant(connection, tournament, nextRound + 1, Math.min(slot, sibling) / 2,
                    other.get().participantId(), locali, boards, new ArrayList<>());
        } else if (other.get().participantId() == null && other.get().matchId() != null
                && other.get().matchId() < 0) {
            avanzaEliminazioneSenzaVincitore(connection, tournament, nextRound,
                    Math.min(slot, sibling) / 2, sourceMatchId);
        }
    }

    /**
     * Risolve contestant per il schedule generator.
     *
     * @param connection connessione JDBC utilizzata dall'operazione
     * @param tournament torneo da elaborare
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     * @param participantId identificativo del partecipante
     * @param locali insieme dei locali
     * @param dartboardPerLocale insieme dei dati relativi a bersaglio per locale
     * @param matchIdsCreate insieme dei dati relativi a partita identificativi create
     * @see java.sql.Connection
     *  
     */
    private void risolviContestant(Connection connection, Tournament tournament, int turno, int slot,
                                    long participantId, List<Long> locali, Map<Long, Long> dartboardPerLocale,
                                    List<Long> matchIdsCreate) {
        bracketSlotDao.upsert(connection, tournament.id(), turno, slot, participantId, null);
        int gemello = slot ^ 1;
        Optional<BracketSlot> slotGemello = bracketSlotDao.find(connection, tournament.id(), turno, gemello);
        if (slotGemello.isPresent() && slotGemello.get().participantId() == null
                && slotGemello.get().matchId() != null && slotGemello.get().matchId() < 0) {
            int slots = prossimaPotenzaDiDue(enrollmentDao.findByTournament(tournament.id()).size());
            if (turno < log2(slots)) risolviContestant(connection, tournament, turno + 1,
                    Math.min(slot, gemello) / 2, participantId, locali, dartboardPerLocale, matchIdsCreate);
            return;
        }
        if (slotGemello.isEmpty() || slotGemello.get().participantId() == null || slotGemello.get().matchId() != null) {
            return;
        }
        long home = Math.min(slot, gemello) == slot ? participantId : slotGemello.get().participantId();
        long away = Math.min(slot, gemello) == slot ? slotGemello.get().participantId() : participantId;
        int producerSlot = Math.min(slot, gemello) / 2;
        int indiceLocale = (turno * 31 + producerSlot) % locali.size();
        long localeId = locali.get(indiceLocale);
        long dartboardId = dartboardPerLocale.get(localeId);

        long matchId = creaPartitaPerCoppia(tournament, home, away, localeId, dartboardId);
        tournamentMatchDao.insert(connection, tournament.id(), matchId, turno, producerSlot);
        tournamentMatchDao.setPairing(connection, tournament.id(), matchId, home, away, localeId);
        bracketSlotDao.upsert(connection, tournament.id(), turno, slot, participantId, matchId);
        bracketSlotDao.upsert(connection, tournament.id(), turno, gemello, slotGemello.get().participantId(), matchId);
        matchIdsCreate.add(matchId);
    }

    /**
     * Crea la partita per la coppia di partecipanti indicata.
     *
     * @param tournament torneo da elaborare
     * @param home partecipante di casa
     * @param away partecipante ospite
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private long creaPartitaPerCoppia(Tournament tournament, long home, long away, long localeId, long dartboardId) {
        String key = "tournament:" + tournament.id() + ":" + Math.min(home, away) + ":" + Math.max(home, away);
        long matchId = matchServiceClient.creaPartita(dartboardId, localeId, tournament.gameTypeId(),
                tournament.modalita().name(), key);
        boolean squadra = tournament.modalita() == ModalitaTorneo.SQUADRA;
        matchServiceClient.aggiungiPartecipante(matchId, squadra ? null : home, squadra ? home : null);
        matchServiceClient.aggiungiPartecipante(matchId, squadra ? null : away, squadra ? away : null);
        return matchId;
    }

    /**
     * Calcola la prima potenza di due non inferiore al numero indicato.
     *
     * @param n numero da elaborare
     * @return la prima potenza di due non inferiore al valore fornito
     *  
     */
    private int prossimaPotenzaDiDue(int n) {
        int potenza = 1;
        while (potenza < n) {
            potenza *= 2;
        }
        return potenza;
    }

    /**
     * Calcola il logaritmo in base due della potenza indicata.
     *
     * @param potenzaDiDue potenza di due da utilizzare
     * @return il logaritmo intero in base due
     *  
     */
    private int log2(int potenzaDiDue) {
        int esponente = 0;
        int valore = potenzaDiDue;
        while (valore > 1) {
            valore /= 2;
            esponente++;
        }
        return esponente;
    }
}
