package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dao.ThrowDao;
import it.uniupo.pissir.darts.match.dto.EdgeCommandDTO;
import it.uniupo.pissir.darts.match.dto.MatchDTO;
import it.uniupo.pissir.darts.match.dto.RosterEntryDTO;
import it.uniupo.pissir.darts.match.dto.ScoreDTO;
import it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchResult;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;

import java.time.Instant;
import java.util.List;

/**
 * Creazione, avvio, chiusura, annullamento di una partita.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class MatchService {

    private final ConnectionProvider connectionProvider;
    private final MatchDao matchDao;
    private final MatchParticipantDao participantDao;
    private final ThrowDao throwDao;
    private final TeamNameResolver teamNameResolver;
    private final ScoringRuleClient scoringRuleClient;
    private final DartboardStatusClient dartboardStatusClient;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final MatchResultService matchResultService;
    private final PlayerNameResolver playerNameResolver;
    private final MatchResultDao resultDao;

    /**
     * Inizializza {@code MatchService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param matchDao componente {@link it.uniupo.pissir.darts.match.dao.MatchDao} utilizzato dall'operazione
     * @param participantDao componente {@link it.uniupo.pissir.darts.match.dao.MatchParticipantDao} utilizzato dall'operazione
     * @param throwDao componente {@link it.uniupo.pissir.darts.match.dao.ThrowDao} utilizzato dall'operazione
     * @param teamNameResolver squadra nome resolver da utilizzare
     * @param scoringRuleClient componente {@link it.uniupo.pissir.darts.match.service.ScoringRuleClient} utilizzato dall'operazione
     * @param dartboardStatusClient componente {@link it.uniupo.pissir.darts.match.service.DartboardStatusClient} utilizzato dall'operazione
     * @param edgeCommandPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher} utilizzato dall'operazione
     * @param matchResultService componente {@link it.uniupo.pissir.darts.match.service.MatchResultService} utilizzato dall'operazione
     * @param playerNameResolver giocatore nome resolver da utilizzare
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchService(ConnectionProvider connectionProvider, MatchDao matchDao, MatchParticipantDao participantDao,
                         ThrowDao throwDao, TeamNameResolver teamNameResolver, ScoringRuleClient scoringRuleClient,
                         DartboardStatusClient dartboardStatusClient, EdgeCommandPublisher edgeCommandPublisher,
                         MatchResultService matchResultService, PlayerNameResolver playerNameResolver) {
        this(connectionProvider, matchDao, participantDao, throwDao, teamNameResolver, scoringRuleClient,
                dartboardStatusClient, edgeCommandPublisher, matchResultService, playerNameResolver,
                new MatchResultDao(connectionProvider));
    }

    /**
     * Inizializza {@code MatchService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param matchDao componente {@link it.uniupo.pissir.darts.match.dao.MatchDao} utilizzato dall'operazione
     * @param participantDao componente {@link it.uniupo.pissir.darts.match.dao.MatchParticipantDao} utilizzato dall'operazione
     * @param throwDao componente {@link it.uniupo.pissir.darts.match.dao.ThrowDao} utilizzato dall'operazione
     * @param teamNameResolver squadra nome resolver da utilizzare
     * @param scoringRuleClient componente {@link it.uniupo.pissir.darts.match.service.ScoringRuleClient} utilizzato dall'operazione
     * @param dartboardStatusClient componente {@link it.uniupo.pissir.darts.match.service.DartboardStatusClient} utilizzato dall'operazione
     * @param edgeCommandPublisher componente {@link it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher} utilizzato dall'operazione
     * @param matchResultService componente {@link it.uniupo.pissir.darts.match.service.MatchResultService} utilizzato dall'operazione
     * @param playerNameResolver giocatore nome resolver da utilizzare
     * @param resultDao componente {@link it.uniupo.pissir.darts.match.dao.MatchResultDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchService(ConnectionProvider connectionProvider, MatchDao matchDao, MatchParticipantDao participantDao,
                         ThrowDao throwDao, TeamNameResolver teamNameResolver, ScoringRuleClient scoringRuleClient,
                         DartboardStatusClient dartboardStatusClient, EdgeCommandPublisher edgeCommandPublisher,
                         MatchResultService matchResultService, PlayerNameResolver playerNameResolver,
                         MatchResultDao resultDao) {
        this.connectionProvider = connectionProvider;
        this.matchDao = matchDao;
        this.participantDao = participantDao;
        this.throwDao = throwDao;
        this.teamNameResolver = teamNameResolver;
        this.scoringRuleClient = scoringRuleClient;
        this.dartboardStatusClient = dartboardStatusClient;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.matchResultService = matchResultService;
        this.playerNameResolver = playerNameResolver;
        this.resultDao = resultDao;
    }

    /**
     * Richiede che la partita esista e sia accessibile.
     *
     * @param id identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Match} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Partita non trovata»
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    public Match requireMatch(long id) {
        return matchDao.findById(id).orElseThrow(() -> new NotFoundException("PARTITA_NON_TROVATA", "Partita non trovata"));
    }

    /**
     * Crea la partita dopo la validazione dei dati.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleId identificativo della regola di punteggio
     * @param modalita modalità della partita o del sensore
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO create(long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                            ModalitaPartita modalita, long createdBy) {
        return create(dartboardId, localeId, gameTypeId, scoringRuleId, modalita, createdBy, null);
    }

    /**
     * Crea la partita dopo la validazione dei dati.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleId identificativo della regola di punteggio
     * @param modalita modalità della partita o del sensore
     * @param createdBy identificativo dell'utente che ha creato la risorsa
     * @param idempotencyKey chiave di riconoscimento della richiesta già elaborata
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws BusinessRuleException se viene rilevato l'errore «Chiave di idempotenza non valida»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO create(long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                            ModalitaPartita modalita, long createdBy, String idempotencyKey) {
        if (idempotencyKey != null) {
            if (!idempotencyKey.startsWith("tournament:")) {
                throw new BusinessRuleException("CHIAVE_NON_VALIDA", "Chiave di idempotenza non valida");
            }
            var existing = matchDao.findByLocalUuid(idempotencyKey);
            if (existing.isPresent()) return toDto(existing.get());
        }
        ScoringRule rule = scoringRuleClient.fetch(scoringRuleId);
        if (rule.gameTypeId() != gameTypeId) {
            throw new BusinessRuleException("REGOLA_NON_COMPATIBILE", "La variante non appartiene al tipo di gioco della partita");
        }
        dartboardStatusClient.requireCompatible(dartboardId, localeId, gameTypeId);
        Match creata;
        try {
            creata = matchDao.insert(new Match(0, dartboardId, localeId, gameTypeId, scoringRuleId, modalita,
                    MatchStatus.CREATA, createdBy, idempotencyKey, 0, null, null));
        } catch (RuntimeException e) {
            if (idempotencyKey != null) {
                var existing = matchDao.findByLocalUuid(idempotencyKey);
                if (existing.isPresent()) return toDto(existing.get());
            }
            throw e;
        }
        return toDto(creata);
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param playerId identificativo del giocatore
     * @param localeId identificativo del locale
     * @param stato stato da impostare o verificare
     * @param dal limite temporale iniziale
     * @param al limite temporale finale
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<MatchDTO> search(Long playerId, Long localeId, MatchStatus stato, Instant dal, Instant al, Page page) {
        List<MatchDTO> lista = matchDao.search(playerId, localeId, stato, dal, al, page).stream().map(this::toDto).toList();
        return PagedResult.of(lista, page, matchDao.countSearch(playerId, localeId, stato, dal, al));
    }

    /**
     * Cerca la partita usando i dati della richiesta.
     *
     * @param id identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO findByIdDto(long id) {
        return toDto(requireMatch(id));
    }

    /**
     * Verifica se l'utente è proprietario del locale.
     *
     * @param localeId identificativo del locale
     * @param userId identificativo dell'utente
     * @return {@code true} se l'utente è proprietario del locale; {@code false} altrimenti
     *  
     */
    public boolean isLocaleOwner(long localeId, long userId) {
        return dartboardStatusClient.isLocaleOwner(localeId, userId);
    }

    /**
     * Avvia l'elaborazione del componente.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public synchronized MatchDTO avvia(long matchId) {
        return avviaFisica(matchId, false);
    }

    /**
     * Annulla una partita non ancora avviata.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita è già avviata e non può essere rimossa come bozza»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public synchronized MatchDTO annullaBozza(long matchId) {
        Match match = requireMatch(matchId);
        if (match.stato() != MatchStatus.CREATA) {
            throw new ConflictException("PARTITA_GIA_AVVIATA", "La partita è già avviata e non può essere rimossa come bozza");
        }
        return annulla(matchId);
    }

    /**
     * Avvia una partita appartenente a un torneo.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita non appartiene a un torneo»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public synchronized MatchDTO avviaTorneo(long matchId) {
        Match match = requireMatch(matchId);
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:"))
            throw new ConflictException("NON_TORNEO", "La partita non appartiene a un torneo");
        if (match.stato() == MatchStatus.IN_CORSO) {
            if (matchDao.isSensorStarted(matchId)) pubblicaAvvioSensore(match);
            return toDto(match);
        }
        if (!matchDao.isSensorTournament(matchId))
            throw new ConflictException("BERSAGLIO_NON_ASSEGNATO", "La partita non ha un bersaglio sensore assegnato");
        return avviaFisica(matchId, true);
    }

    /**
     * Indica se sensore avviata per i dati forniti.
     *
     * @param matchId identificativo della partita
     * @return {@code true} se sensore avviata per i dati forniti; {@code false} altrimenti
     *  
     */
    public boolean isSensorStarted(long matchId) {
        return matchDao.isSensorStarted(matchId);
    }

    /**
     * Avvia la partita usando il bersaglio e il dispositivo edge assegnati.
     *
     * @param matchId identificativo della partita
     * @param torneo torneo a cui si riferiscono i dati
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita non è nello stato CREATA»
     * @throws BusinessRuleException se viene rilevato l'errore «PARTECIPANTI_INSUFFICIENTI»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    private MatchDTO avviaFisica(long matchId, boolean torneo) {
        Match match = requireMatch(matchId);
        if (match.stato() != MatchStatus.CREATA) {
            throw new ConflictException("PARTITA_NON_AVVIABILE", "La partita non è nello stato CREATA");
        }
        List<MatchParticipant> partecipanti = participantDao.findByMatch(matchId);
        if (partecipanti.isEmpty() || (torneo && partecipanti.size() != 2)) {
            throw new BusinessRuleException("PARTECIPANTI_INSUFFICIENTI",
                    torneo ? "Servono due partecipanti per avviare la partita" :
                            "Serve almeno un partecipante per avviare la partita");
        }
        ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());
        List<RosterEntryDTO> roster = costruisciRoster(partecipanti);
        ScoringRuleSnapshotDTO snapshot = toSnapshot(regola);

        if (torneo) edgeCommandPublisher.requireAvailable();

        dartboardStatusClient.setInUso(match.dartboardId(), match.localeId(), match.gameTypeId());

        Match avviata;
        try {
            avviata = connectionProvider.withTransaction(connection -> {
                for (MatchParticipant partecipante : partecipanti) {
                    MatchParticipant conPunteggio = new MatchParticipant(partecipante.id(), partecipante.matchId(),
                            partecipante.playerId(), partecipante.teamId(), partecipante.ordineTurno(),
                            regola.punteggioIniziale(), partecipante.ritirato());
                    participantDao.update(connection, conPunteggio);
                }
                Match aggiornata = new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                        match.scoringRuleId(), match.modalita(), MatchStatus.IN_CORSO, match.createdBy(), match.localUuid(),
                        match.clockOffsetMs(), Instant.now(), null);
                matchDao.update(connection, aggiornata);
                if (torneo) matchDao.markSensorStarted(connection, match.id());
                return aggiornata;
            });
        } catch (RuntimeException e) {
            dartboardStatusClient.setDisponibile(match.dartboardId());
            throw e;
        }

        EdgeCommandDTO command = EdgeCommandDTO.startMatch(match.id(), snapshot, roster);
        try {
            if (torneo) edgeCommandPublisher.publishCommandRequired(match.localeId(), match.dartboardId(), command);
            else edgeCommandPublisher.publishCommandOrThrow(match.localeId(), match.dartboardId(), command);
        } catch (RuntimeException e) {
            Match annullata = new Match(avviata.id(), avviata.dartboardId(), avviata.localeId(), avviata.gameTypeId(),
                    avviata.scoringRuleId(), avviata.modalita(), MatchStatus.ANNULLATA, avviata.createdBy(),
                    avviata.localUuid(), avviata.clockOffsetMs(), avviata.dataInizio(), Instant.now());
            connectionProvider.withTransaction(connection -> {
                matchDao.update(connection, annullata);
                return null;
            });
            edgeCommandPublisher.publishCommand(match.localeId(), match.dartboardId(), EdgeCommandDTO.endMatch(match.id()));
            dartboardStatusClient.setDisponibile(match.dartboardId());
            throw e;
        }

        return toDto(avviata);
    }

    /**
     * Pubblica avvio sensore tramite il collegamento MQTT.
     *
     * @param match partita da elaborare
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private void pubblicaAvvioSensore(Match match) {
        ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());
        List<RosterEntryDTO> roster = costruisciRoster(participantDao.findByMatch(match.id()));
        edgeCommandPublisher.publishCommandRequired(match.localeId(), match.dartboardId(),
                EdgeCommandDTO.startMatch(match.id(), toSnapshot(regola), roster));
    }

    /**
     * Avvia la partita sul bersaglio virtuale condiviso.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «Partita virtuale non appartenente a un torneo»
     * @throws BusinessRuleException se viene rilevato l'errore «Servono due partecipanti»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public synchronized MatchDTO avviaVirtuale(long matchId) {
        Match match = requireMatch(matchId);
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:"))
            throw new ConflictException("NON_TORNEO", "Partita virtuale non appartenente a un torneo");
        if (match.stato() == MatchStatus.IN_CORSO) return toDto(match);
        if (match.stato() != MatchStatus.CREATA)
            throw new ConflictException("PARTITA_NON_AVVIABILE", "La partita è già conclusa");
        List<MatchParticipant> participants = participantDao.findByMatch(matchId);
        if (participants.size() != 2)
            throw new BusinessRuleException("PARTECIPANTI_INSUFFICIENTI", "Servono due partecipanti");
        ScoringRule rule = scoringRuleClient.fetch(match.scoringRuleId());
        Match started = connectionProvider.withTransaction(connection -> {
            for (MatchParticipant participant : participants)
                participantDao.update(connection, new MatchParticipant(participant.id(), participant.matchId(),
                        participant.playerId(), participant.teamId(), participant.ordineTurno(),
                        rule.punteggioIniziale(), participant.ritirato()));
            Match updated = new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                    match.scoringRuleId(), match.modalita(), MatchStatus.IN_CORSO, match.createdBy(), match.localUuid(),
                    match.clockOffsetMs(), Instant.now(), null);
            matchDao.update(connection, updated);
            return updated;
        });
        return toDto(started);
    }

    /**
     * Determina l'esito della partita in caso di assenza.
     *
     * @param matchId identificativo della partita
     * @param winnerPlayerId identificativo del giocatore vincitore
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita non appartiene a un torneo»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public synchronized MatchDTO resolveAbsence(long matchId, Long winnerPlayerId) {
        Match match = requireMatch(matchId);
        if (match.localUuid() == null || !match.localUuid().startsWith("tournament:"))
            throw new ConflictException("NON_TORNEO", "La partita non appartiene a un torneo");
        List<MatchParticipant> participants = participantDao.findByMatch(matchId);
        if (participants.size() != 2 || (winnerPlayerId != null && participants.stream()
                .noneMatch(p -> winnerPlayerId.equals(p.playerId()))))
            throw new ConflictException("PARTECIPANTI_NON_VALIDI", "I due partecipanti non corrispondono");
        if (match.stato() == MatchStatus.CREATA) {
            connectionProvider.withTransaction(connection -> {
                matchResultService.chiudiPerAssenzaSuConnessione(connection, match, participants, winnerPlayerId);
                return null;
            });
        } else if (match.stato() == MatchStatus.COMPLETATA) {
            boolean same = connectionProvider.withConnection(connection -> {
                try (java.sql.PreparedStatement ps = connection.prepareStatement(
                        "SELECT winner_player_id FROM match_absences WHERE match_id=?")) {
                    ps.setLong(1, matchId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return false;
                        Long stored = rs.getObject(1) == null ? null : rs.getLong(1);
                        return java.util.Objects.equals(stored, winnerPlayerId);
                    }
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            });
            if (!same) throw new ConflictException("ESITO_GIA_REGISTRATO", "Esito già registrato con dati diversi");
        } else {
            throw new ConflictException("PARTITA_GIA_AVVIATA", "La partita è già in corso");
        }
        Match completed = requireMatch(matchId);
        matchResultService.pubblicaChiusura(completed, participants, resultDao.findByMatch(matchId));
        return toDto(completed);
    }

    /**
     * Compone l'evento del risultato conclusivo della partita.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «Risultato torneo non disponibile»
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO completedEvent(long matchId) {
        Match match = requireMatch(matchId);
        if (match.stato() != MatchStatus.COMPLETATA || match.localUuid() == null
                || !match.localUuid().startsWith("tournament:"))
            throw new ConflictException("PARTITA_NON_COMPLETATA", "Risultato torneo non disponibile");
        return matchResultService.completedEvent(match, participantDao.findByMatch(matchId),
                resultDao.findByMatch(matchId));
    }

    /**
     * Ripubblica esito per la partita.
     *
     * @param matchId identificativo della partita
     * @throws ConflictException se viene rilevato l'errore «La partita non è completata»
     *  
     */
    public void ripubblicaEsito(long matchId) {
        Match match = requireMatch(matchId);
        if (match.stato() != MatchStatus.COMPLETATA && match.stato() != MatchStatus.SINCRONIZZATA) {
            throw new ConflictException("PARTITA_NON_COMPLETATA", "La partita non è completata");
        }
        List<MatchResult> results = resultDao.findByMatch(matchId);
        if (results.isEmpty()) {
            throw new ConflictException("RISULTATI_ASSENTI", "La partita non ha risultati da ripubblicare");
        }
        matchResultService.ripubblicaSoloEvento(match, participantDao.findByMatch(matchId), results);
    }

    /**
     * Ripubblica partite attive per la partita. 
     */
    public void ripubblicaPartiteAttive() {
        int pagina = 0;
        List<Match> attive;
        do {
            attive = matchDao.search(null, null, MatchStatus.IN_CORSO, null, null,
                    new it.uniupo.pissir.darts.common.persistence.Page(pagina++, 100));
            for (Match match : attive) {
                if (match.localUuid() != null && match.localUuid().startsWith("tournament:")
                        && !matchDao.isSensorStarted(match.id())) continue;
                try {
                    ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());
                    List<RosterEntryDTO> roster = costruisciRoster(participantDao.findByMatch(match.id()));
                    edgeCommandPublisher.publishCommandOrThrow(match.localeId(), match.dartboardId(),
                            EdgeCommandDTO.startMatch(match.id(), toSnapshot(regola), roster));
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(MatchService.class).warn(
                            "Ripubblicazione START_MATCH fallita per partita {}: {}", match.id(), e.getMessage());
                }
            }
        } while (attive.size() == 100);
    }

    /**
     * Conclude la partita e libera il bersaglio.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita non è in corso»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO fine(long matchId) {
        Match match = requireMatch(matchId);
        if (match.stato() != MatchStatus.IN_CORSO) {
            throw new ConflictException("PARTITA_NON_IN_CORSO", "La partita non è in corso");
        }
        List<MatchParticipant> partecipanti = participantDao.findAttiviByMatch(matchId);
        ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());

        List<MatchResult> risultati = connectionProvider.withTransaction(connection ->
                matchResultService.chiudiSuConnessione(connection, match, partecipanti, regola, null));

        Match completata = requireMatch(matchId);
        matchResultService.pubblicaChiusura(completata, partecipanti, risultati);
        return toDto(completata);
    }

    /**
     * Annulla la partita dopo averne verificato lo stato.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.MatchDTO} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «La partita è già conclusa»
     * @see it.uniupo.pissir.darts.match.dto.MatchDTO
     *  
     */
    public MatchDTO annulla(long matchId) {
        Match match = requireMatch(matchId);
        if (match.stato() == MatchStatus.COMPLETATA || match.stato() == MatchStatus.SINCRONIZZATA
                || match.stato() == MatchStatus.ANNULLATA) {
            throw new ConflictException("PARTITA_NON_ANNULLABILE", "La partita è già conclusa");
        }
        if (match.stato() == MatchStatus.IN_CORSO &&
                (match.localUuid() == null || !match.localUuid().startsWith("tournament:"))) {
            return fine(matchId);
        }
        boolean eraInCorso = match.stato() == MatchStatus.IN_CORSO;

        Match annullata = connectionProvider.withTransaction(connection -> {
            Match aggiornata = new Match(match.id(), match.dartboardId(), match.localeId(), match.gameTypeId(),
                    match.scoringRuleId(), match.modalita(), MatchStatus.ANNULLATA, match.createdBy(), match.localUuid(),
                    match.clockOffsetMs(), match.dataInizio(), Instant.now());
            matchDao.update(connection, aggiornata);
            return aggiornata;
        });

        if (eraInCorso) {
            edgeCommandPublisher.publishCommand(match.localeId(), match.dartboardId(), EdgeCommandDTO.endMatch(match.id()));
            dartboardStatusClient.setDisponibile(match.dartboardId());
        }
        return toDto(annullata);
    }

    /**
     * Legge il punteggio corrente della partita o del giocatore richiesto.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.ScoreDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ScoreDTO
     *  
     */
    public ScoreDTO punteggio(long matchId) {
        Match match = requireMatch(matchId);
        List<MatchParticipant> attivi = participantDao.findAttiviByMatch(matchId);
        Long giocatoreAttivo = null;
        if (match.stato() == MatchStatus.IN_CORSO && !attivi.isEmpty()) {
            ScoringRule regola = scoringRuleClient.fetch(match.scoringRuleId());
            List<it.uniupo.pissir.darts.match.model.Throw> rilevanti = connectionProvider.withConnection(
                    connection -> throwDao.findRilevantiByMatch(connection, matchId));
            giocatoreAttivo = TurnManager.calcolaTurnoCorrente(attivi, rilevanti, regola).participantId();
        }
        List<ScoreDTO.ParticipantScoreDTO> punteggi = attivi.stream()
                .map(p -> new ScoreDTO.ParticipantScoreDTO(p.id(), p.playerId(), p.teamId(),
                        teamNameResolver.resolveOrNull(p.teamId()), nomeGiocatoreOrNull(p),
                        p.ordineTurno(), p.punteggioCorrente(), p.ritirato()))
                .toList();
        return new ScoreDTO(matchId, match.stato().name(), giocatoreAttivo, punteggi);
    }

    /**
     * Legge la formazione dei partecipanti della partita.
     *
     * @param matchId identificativo della partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<RosterEntryDTO> roster(long matchId) {
        requireMatch(matchId);
        return costruisciRoster(participantDao.findByMatch(matchId));
    }

    /**
     * Legge i tiri registrati per la partita.
     *
     * @param matchId identificativo della partita
     * @param page parametri di paginazione
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.persistence.PagedResult} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public it.uniupo.pissir.darts.common.persistence.PagedResult<it.uniupo.pissir.darts.match.dto.ThrowDTO> listaTiri(
            long matchId, it.uniupo.pissir.darts.common.persistence.Page page) {
        requireMatch(matchId);
        List<it.uniupo.pissir.darts.match.dto.ThrowDTO> lista = throwDao.findByMatch(matchId, page).stream()
                .map(this::toThrowDto)
                .toList();
        return it.uniupo.pissir.darts.common.persistence.PagedResult.of(lista, page, throwDao.countByMatch(matchId));
    }

    /**
     * Converte il tiro nel formato esposto dalle API.
     *
     * @param t dato {@link it.uniupo.pissir.darts.match.model.Throw} da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.ThrowDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ThrowDTO
     *  
     */
    private it.uniupo.pissir.darts.match.dto.ThrowDTO toThrowDto(it.uniupo.pissir.darts.match.model.Throw t) {
        return new it.uniupo.pissir.darts.match.dto.ThrowDTO(t.id(), t.matchId(), t.participantId(), t.tipo(),
                t.settore() == null ? null : t.settore().name(),
                t.moltiplicatore() == null ? null : t.moltiplicatore().name(), t.teamId(), t.punti(), t.numeroTurno(),
                t.esito().name(), t.annullato(), t.timestampSensore().toString(), t.timestampRicezione().toString(),
                t.origine().name(), t.dartboardId());
    }

    /**
     * Costruisce roster per la partita.
     *
     * @param partecipanti partecipanti associati alla partita
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private List<RosterEntryDTO> costruisciRoster(List<MatchParticipant> partecipanti) {
        return partecipanti.stream()
                .map(p -> new RosterEntryDTO(p.id(), nomeDi(p), p.ordineTurno(), p.playerId(), p.teamId()))
                .toList();
    }

    /**
     * Recupera il nome pubblico del partecipante.
     *
     * @param partecipante partecipante alla partita da elaborare
     * @return il testo o il valore di configurazione richiesto
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private String nomeDi(MatchParticipant partecipante) {
        if (partecipante.playerId() != null) {
            return playerNameResolver.resolve(partecipante.playerId());
        }
        return teamNameResolver.resolve(partecipante.teamId());
    }

    /**
     * Recupera il nome del giocatore, oppure restituisce un valore assente.
     *
     * @param partecipante partecipante alla partita da elaborare
     * @return il testo o il valore di configurazione richiesto
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private String nomeGiocatoreOrNull(MatchParticipant partecipante) {
        return partecipante.playerId() == null ? null : playerNameResolver.resolve(partecipante.playerId());
    }

    /**
     * Converte la regola nei dati da conservare con la partita.
     *
     * @param regola regola di punteggio da applicare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO
     *  
     */
    private ScoringRuleSnapshotDTO toSnapshot(ScoringRule regola) {
        return new ScoringRuleSnapshotDTO(regola.variante(), regola.direzione().name(), regola.punteggioIniziale(),
                regola.punteggioObiettivo(), regola.chiusuraDouble(), regola.bustAnnullaTurno(), regola.maxTiriPerTurno());
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
                match.dataFine() == null ? null : match.dataFine().toString(),
                matchDao.isSensorTournament(match.id()));
    }
}
