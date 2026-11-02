package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.tournament.dao.EnrollmentDao;
import it.uniupo.pissir.darts.tournament.dto.EnrollmentDTO;
import it.uniupo.pissir.darts.tournament.model.Enrollment;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.TipoIscrizione;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Iscrizioni individuali o a squadre.
 *
 * @see it.uniupo.pissir.darts.tournament.dao.EnrollmentDao
 *  
 */
public final class EnrollmentService {

    private final EnrollmentDao enrollmentDao;
    private final TournamentService tournamentService;
    private final UserClient userClient;
    private final MatchServiceClient matchServiceClient;

    /**
     * Inizializza {@code EnrollmentService} con i dati e le dipendenze indicati.
     *
     * @param enrollmentDao componente {@link it.uniupo.pissir.darts.tournament.dao.EnrollmentDao} utilizzato dall'operazione
     * @param tournamentService componente {@link it.uniupo.pissir.darts.tournament.service.TournamentService} utilizzato dall'operazione
     * @param userClient componente {@link it.uniupo.pissir.darts.tournament.service.UserClient} utilizzato dall'operazione
     * @param matchServiceClient componente {@link it.uniupo.pissir.darts.tournament.service.MatchServiceClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.tournament.dao.EnrollmentDao
     *  
     */
    public EnrollmentService(EnrollmentDao enrollmentDao, TournamentService tournamentService, UserClient userClient,
                              MatchServiceClient matchServiceClient) {
        this.enrollmentDao = enrollmentDao;
        this.tournamentService = tournamentService;
        this.userClient = userClient;
        this.matchServiceClient = matchServiceClient;
    }

    /**
     * Cerca iscrizioni ai tornei per torneo.
     *
     * @param tournamentId identificativo del torneo
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<EnrollmentDTO> findByTournament(long tournamentId) {
        tournamentService.requireTournament(tournamentId);
        return enrollmentDao.findByTournament(tournamentId).stream().map(this::toDto).toList();
    }

    /**
     * Cerca iscrizioni ai tornei torneo identificativi per partecipante.
     *
     * @param participantId identificativo del partecipante
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Long> findTournamentIdsByParticipant(long participantId) {
        return enrollmentDao.findTournamentIdsByParticipant(participantId);
    }

    /**
     * Iscrive il partecipante al torneo.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Enrollment} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: participantId»
     * @throws ConflictException se viene rilevato l'errore «Le iscrizioni sono ammesse solo con torneo APERTO»
     * @see it.uniupo.pissir.darts.tournament.model.Enrollment
     *  
     */
    public Enrollment enroll(long tournamentId, Long participantId) {
        if (participantId == null) {
            throw new ValidationException("Campo obbligatorio mancante: participantId");
        }
        Tournament tournament = tournamentService.requireTournament(tournamentId);
        tournamentService.requireModificabile(tournament);
        if (tournament.stato() != TournamentStatus.APERTO) {
            throw new ConflictException("TORNEO_NON_APERTO", "Le iscrizioni sono ammesse solo con torneo APERTO");
        }
        if (!Instant.now().isBefore(tournament.dataInizio())) {
            throw new ConflictException("ISCRIZIONI_CHIUSE", "Le iscrizioni sono chiuse dalla data e ora di inizio del torneo");
        }
        TipoIscrizione tipo;
        if (tournament.modalita() == ModalitaTorneo.INDIVIDUALE) {
            userClient.requireExists(participantId);
            tipo = TipoIscrizione.GIOCATORE;
        } else {
            matchServiceClient.requireTeamExists(participantId);
            tipo = TipoIscrizione.SQUADRA;
        }
        if (enrollmentDao.find(tournamentId, participantId).isPresent()) {
            throw new ConflictException("GIA_ISCRITTO", "Il partecipante " + participantId + " è già iscritto al torneo");
        }
        long id = enrollmentDao.insert(tournamentId, participantId, tipo, Instant.now());
        return new Enrollment(id, tournamentId, participantId, tipo, Instant.now());
    }

    /**
     * Registra il ritiro del partecipante dal torneo.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     * @throws ConflictException se viene rilevato l'errore «Le iscrizioni sono chiuse dalla data e ora di inizio del torneo»
     * @throws NotFoundException se i dati o lo stato del componente non consentono l'operazione
     *  
     */
    public void withdraw(long tournamentId, long participantId) {
        Tournament tournament = tournamentService.requireTournament(tournamentId);
        tournamentService.requireModificabile(tournament);
        if (!Instant.now().isBefore(tournament.dataInizio())) {
            throw new ConflictException("ISCRIZIONI_CHIUSE", "Le iscrizioni sono chiuse dalla data e ora di inizio del torneo");
        }
        if (enrollmentDao.find(tournamentId, participantId).isEmpty()) {
            throw new NotFoundException("Iscrizione non trovata per il partecipante " + participantId);
        }
        enrollmentDao.delete(tournamentId, participantId);
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param e eccezione da gestire
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dto.EnrollmentDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dto.EnrollmentDTO
     *  
     */
    private EnrollmentDTO toDto(Enrollment e) {
        return new EnrollmentDTO(e.id(), e.tournamentId(), e.participantId(), e.tipo().name(), e.dataIscrizione().toString());
    }
}
