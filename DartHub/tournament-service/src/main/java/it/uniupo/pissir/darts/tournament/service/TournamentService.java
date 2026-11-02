package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.dto.TournamentDTO;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Standing;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentMatch;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Un torneo ha un solo gameTypeId e uno o più locali.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
 *  
 */
public final class TournamentService {
    /**
     * Conta le iscrizioni al torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return il valore numerico calcolato o letto
     *  
     */
    public long enrollmentCount(long tournamentId) { return tournamentDao.enrollmentCount(tournamentId); }

    private final TournamentDao tournamentDao;
    private final TournamentLocaleDao tournamentLocaleDao;
    private final TournamentMatchDao tournamentMatchDao;
    private final StandingDao standingDao;
    private final GameTypeClient gameTypeClient;
    private final LocaleClient localeClient;
    private final MatchServiceClient matchServiceClient;

    /**
     * Inizializza {@code TournamentService} con i dati e le dipendenze indicati.
     *
     * @param tournamentDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentDao} utilizzato dall'operazione
     * @param tournamentLocaleDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao} utilizzato dall'operazione
     * @param tournamentMatchDao componente {@link it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao} utilizzato dall'operazione
     * @param standingDao componente {@link it.uniupo.pissir.darts.tournament.dao.StandingDao} utilizzato dall'operazione
     * @param gameTypeClient componente {@link it.uniupo.pissir.darts.tournament.service.GameTypeClient} utilizzato dall'operazione
     * @param localeClient componente {@link it.uniupo.pissir.darts.tournament.service.LocaleClient} utilizzato dall'operazione
     * @param matchServiceClient componente {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
     *  
     */
    public TournamentService(TournamentDao tournamentDao, TournamentLocaleDao tournamentLocaleDao,
                              TournamentMatchDao tournamentMatchDao, StandingDao standingDao,
                              GameTypeClient gameTypeClient, LocaleClient localeClient, MatchServiceClient matchServiceClient) {
        this.tournamentDao = tournamentDao;
        this.tournamentLocaleDao = tournamentLocaleDao;
        this.tournamentMatchDao = tournamentMatchDao;
        this.standingDao = standingDao;
        this.gameTypeClient = gameTypeClient;
        this.localeClient = localeClient;
        this.matchServiceClient = matchServiceClient;
    }

    /**
     * Crea il torneo dopo la validazione dei dati.
     *
     * @param nome nome della risorsa
     * @param gameTypeId identificativo del gioco
     * @param modalita modalità della partita o del sensore
     * @param formula formula del torneo
     * @param puntiVittoria punti in classifica assegnati per una vittoria
     * @param puntiPareggio punti in classifica assegnati per un pareggio
     * @param puntiSconfitta punti in classifica assegnati per una sconfitta
     * @param dataInizio data di inizio del torneo
     * @param dataFine data di fine del torneo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: nome»
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public Tournament create(String nome, Long gameTypeId, String modalita, String formula, Integer puntiVittoria,
                              Integer puntiPareggio, Integer puntiSconfitta, String dataInizio, String dataFine) {
        if (nome == null || nome.isBlank()) {
            throw new ValidationException("Campo obbligatorio mancante: nome");
        }
        if (gameTypeId == null) {
            throw new ValidationException("Campo obbligatorio mancante: gameTypeId");
        }
        Instant inizio = parseRequiredInstant(dataInizio, "dataInizio");
        Instant fine = parseRequiredInstant(dataFine, "dataFine");
        if (!inizio.isAfter(Instant.now())) {
            throw new ValidationException("La data di inizio deve essere futura");
        }
        if (!fine.isAfter(inizio)) {
            throw new ValidationException("La data di fine deve essere successiva all'inizio");
        }
        ModalitaTorneo modalitaTorneo = parseModalita(modalita);
        FormulaTorneo formulaTorneo = formula == null ? FormulaTorneo.GIRONE : parseFormula(formula);
        gameTypeClient.requireExists(gameTypeId);

        Tournament nuovo = new Tournament(0, nome, gameTypeId, modalitaTorneo, formulaTorneo,
                puntiVittoria == null ? 3 : puntiVittoria, puntiPareggio == null ? 1 : puntiPareggio,
                puntiSconfitta == null ? 0 : puntiSconfitta, inizio, fine, TournamentStatus.APERTO, null);
        return tournamentDao.insert(nuovo);
    }

    /**
     * Richiede che il torneo esista e sia accessibile.
     *
     * @param id identificativo del torneo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Torneo non trovato:»
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public Tournament requireTournament(long id) {
        return tournamentDao.findById(id).orElseThrow(() -> new NotFoundException("Torneo non trovato: " + id));
    }

    /**
     * Cerca il torneo usando i dati della richiesta.
     *
     * @param id identificativo del torneo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dto.TournamentDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dto.TournamentDTO
     *  
     */
    public TournamentDTO findByIdDto(long id) {
        return toDto(requireTournament(id));
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<TournamentDTO> search(Page page) {
        List<TournamentDTO> lista = tournamentDao.findAll(page).stream().map(this::toDto).toList();
        return PagedResult.of(lista, page, tournamentDao.count());
    }

    /**
     * Aggiorna il torneo dopo i controlli previsti.
     *
     * @param id identificativo del torneo
     * @param nome nome della risorsa
     * @param puntiVittoria punti in classifica assegnati per una vittoria
     * @param puntiPareggio punti in classifica assegnati per un pareggio
     * @param puntiSconfitta punti in classifica assegnati per una sconfitta
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public Tournament update(long id, String nome, Integer puntiVittoria, Integer puntiPareggio, Integer puntiSconfitta) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        Tournament aggiornato = new Tournament(tournament.id(), nome == null || nome.isBlank() ? tournament.nome() : nome,
                tournament.gameTypeId(), tournament.modalita(), tournament.formula(),
                puntiVittoria == null ? tournament.puntiVittoria() : puntiVittoria,
                puntiPareggio == null ? tournament.puntiPareggio() : puntiPareggio,
                puntiSconfitta == null ? tournament.puntiSconfitta() : puntiSconfitta,
                tournament.dataInizio(), tournament.dataFine(), tournament.stato(), tournament.vincitoreParticipantId(),
                tournament.dataInizioEffettiva(), tournament.dataFineEffettiva());
        tournamentDao.update(aggiornato);
        return aggiornato;
    }

    /**
     * Rimuove il torneo verificando i vincoli dell'operazione.
     *
     * @param id identificativo del torneo
     *  
     */
    public void delete(long id) {
        requireTournament(id);
        tournamentDao.delete(id);
    }

    /**
     * Associa un locale al torneo.
     *
     * @param id identificativo del torneo
     * @param localeId identificativo del locale
     *  
     */
    public void addLocale(long id, long localeId) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        localeClient.requireExists(localeId);
        tournamentLocaleDao.insert(id, localeId);
    }

    /**
     * Rimuove l'associazione fra locale e torneo.
     *
     * @param id identificativo del torneo
     * @param localeId identificativo del locale
     *  
     */
    public void removeLocale(long id, long localeId) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        tournamentLocaleDao.delete(id, localeId);
    }

    /**
     * Associa una partita al torneo.
     *
     * @param id identificativo del torneo
     * @param matchId identificativo della partita
     * @throws BusinessRuleException se viene rilevato l'errore «GIOCO_NON_COMPATIBILE»
     *  
     */
    public void addMatch(long id, long matchId) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        long gameTypeId = matchServiceClient.gameTypeIdOf(matchId);
        if (gameTypeId != tournament.gameTypeId()) {
            throw new BusinessRuleException("GIOCO_NON_COMPATIBILE",
                    "La partita " + matchId + " è di un tipo di gioco diverso da quello del torneo");
        }
        if (tournamentMatchDao.findByTournamentAndMatch(id, matchId).isEmpty()) {
            tournamentMatchDao.insert(id, matchId, 1, null);
        }
    }

    /**
     * Rimuove una partita dal torneo.
     *
     * @param id identificativo del torneo
     * @param matchId identificativo della partita
     *  
     */
    public void removeMatch(long id, long matchId) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        tournamentMatchDao.delete(id, matchId);
    }

    /**
     * Ferma l'attività e chiude le risorse del componente.
     *
     * @param id identificativo del torneo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @throws BusinessRuleException se viene rilevato l'errore «Il torneo non ha nessun locale associato»
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public Tournament chiudi(long id) {
        Tournament tournament = requireTournament(id);
        requireModificabile(tournament);
        if (tournamentLocaleDao.findLocaliIds(id).isEmpty()) {
            throw new BusinessRuleException("TORNEO_SENZA_LOCALI", "Il torneo non ha nessun locale associato");
        }
        List<TournamentMatch> partite = tournamentMatchDao.findByTournament(id);
        for (TournamentMatch partita : partite) {
            if (!"COMPLETATA".equals(matchServiceClient.statoOf(partita.matchId()))) {
                throw new BusinessRuleException("PARTITE_NON_COMPLETATE",
                        "Esistono partite del torneo non ancora completate (es. " + partita.matchId() + ")");
            }
        }
        List<Standing> classifica = standingDao.findByTournament(id);
        Long vincitore = classifica.isEmpty() ? null : classifica.get(0).participantId();
        if (tournament.formula() == FormulaTorneo.ELIMINAZIONE && !partite.isEmpty()) {
            int lastRound = partite.stream().mapToInt(TournamentMatch::turno).max().orElse(1);
            java.util.Set<Long> winners = new java.util.HashSet<>();
            for (TournamentMatch match : partite) {
                if (match.turno() != lastRound) continue;
                var event = matchServiceClient.completedEvent(match.matchId());
                for (var result : event.risultati()) {
                    if (result.vincitore()) {
                        Long participantId = tournament.modalita() == ModalitaTorneo.INDIVIDUALE
                                ? result.playerId() : result.teamId();
                        if (participantId != null) winners.add(participantId);
                    }
                }
            }
            if (winners.size() > 1) throw new BusinessRuleException("TABELLONE_NON_CONCLUSO",
                    "Il tabellone deve completare il turno successivo");
            vincitore = winners.stream().findFirst().orElse(null);
        }
        return salvaChiusura(tournament, vincitore, Instant.now());
    }

    /**
     * Chiude scaduto per il torneo.
     *
     * @param id identificativo del torneo
     * @param now istante corrente usato nei controlli
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public Tournament chiudiScaduto(long id, Instant now) {
        Tournament tournament = requireTournament(id);
        if (tournament.stato() == TournamentStatus.CHIUSO || tournament.dataFine() == null
                || tournament.dataFine().isAfter(now)) return tournament;
        if (tournamentMatchDao.findByTournament(id).isEmpty()) {
            return salvaChiusura(tournament, null, now);
        }
        return chiudi(id);
    }

    /**
     * Salva chiusura per il torneo.
     *
     * @param tournament torneo da elaborare
     * @param vincitore partecipante vincitore
     * @param fineEffettiva istante di conclusione effettiva
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private Tournament salvaChiusura(Tournament tournament, Long vincitore, Instant fineEffettiva) {
        Tournament chiuso = new Tournament(tournament.id(), tournament.nome(), tournament.gameTypeId(),
                tournament.modalita(), tournament.formula(), tournament.puntiVittoria(), tournament.puntiPareggio(),
                tournament.puntiSconfitta(), tournament.dataInizio(), tournament.dataFine(), TournamentStatus.CHIUSO,
                vincitore, tournament.dataInizioEffettiva(), fineEffettiva);
        tournamentDao.update(chiuso);
        return chiuso;
    }

    /**
     * Segna il torneo come avviato.
     *
     * @param id identificativo del torneo
     *  
     */
    public void segnaInCorso(long id) {
        Tournament tournament = requireTournament(id);
        if (tournament.stato() == TournamentStatus.APERTO) {
            Tournament aggiornato = new Tournament(tournament.id(), tournament.nome(), tournament.gameTypeId(),
                    tournament.modalita(), tournament.formula(), tournament.puntiVittoria(), tournament.puntiPareggio(),
                    tournament.puntiSconfitta(), tournament.dataInizio(), tournament.dataFine(), TournamentStatus.IN_CORSO,
                    tournament.vincitoreParticipantId(), Instant.now(), tournament.dataFineEffettiva());
            tournamentDao.update(aggiornato);
        }
    }

    /**
     * Verifica che la risorsa possa essere modificata.
     *
     * @param tournament torneo da elaborare
     * @throws ConflictException se viene rilevato l'errore «Il torneo è chiuso: nessuna modifica è più ammessa»
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    public void requireModificabile(Tournament tournament) {
        if (tournament.stato() == TournamentStatus.CHIUSO) {
            throw new ConflictException("TORNEO_CHIUSO", "Il torneo è chiuso: nessuna modifica è più ammessa");
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param t torneo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dto.TournamentDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dto.TournamentDTO
     *  
     */
    private TournamentDTO toDto(Tournament t) {
        List<Long> locali = tournamentLocaleDao.findLocaliIds(t.id());
        String esito = t.stato() == TournamentStatus.IN_CORSO && !tournamentMatchDao.esistonoPartite(t.id())
                && tournamentDao.enrollmentCount(t.id()) < 2 ? "ISCRITTI_INSUFFICIENTI" : null;
        return new TournamentDTO(t.id(), t.nome(), t.gameTypeId(), t.modalita().name(), t.formula().name(),
                t.puntiVittoria(), t.puntiPareggio(), t.puntiSconfitta(), t.dataInizio().toString(),
                t.dataFine() == null ? null : t.dataFine().toString(), t.stato().name(), t.vincitoreParticipantId(), locali,
                t.dataInizioEffettiva() == null ? null : t.dataInizioEffettiva().toString(),
                t.dataFineEffettiva() == null ? null : t.dataFineEffettiva().toString(), esito);
    }

    /**
     * Legge e converte required instant dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @param field nome del campo da leggere
     * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
     * @throws ValidationException se i dati o lo stato del componente non consentono l'operazione
     * @see java.time.Instant
     *  
     */
    private Instant parseRequiredInstant(String value, String field) {
        if (value == null || value.isBlank()) throw new ValidationException("Campo obbligatorio mancante: " + field);
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new ValidationException(field + " deve includere data, ora e fuso orario (ISO 8601)");
        }
    }

    /**
     * Legge e converte modalita dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.ModalitaTorneo} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.ModalitaTorneo
     *  
     */
    private ModalitaTorneo parseModalita(String value) {
        try {
            return ModalitaTorneo.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Modalità non valida: " + value);
        }
    }

    /**
     * Legge e converte formula dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.FormulaTorneo} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.FormulaTorneo
     *  
     */
    private FormulaTorneo parseFormula(String value) {
        try {
            return FormulaTorneo.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Formula non valida: " + value);
        }
    }
}
