package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.match.dao.MatchParticipantDao;
import it.uniupo.pissir.darts.match.dto.ParticipantDTO;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;

import java.util.List;

/**
 * Iscrizione/rimozione dei partecipanti, ordine dei turni.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class ParticipantService {

    private final ConnectionProvider connectionProvider;
    private final MatchParticipantDao participantDao;
    private final MatchService matchService;

    /**
     * Inizializza {@code ParticipantService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param participantDao componente {@link it.uniupo.pissir.darts.match.dao.MatchParticipantDao} utilizzato dall'operazione
     * @param matchService componente {@link it.uniupo.pissir.darts.match.service.MatchService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public ParticipantService(ConnectionProvider connectionProvider, MatchParticipantDao participantDao,
                               MatchService matchService) {
        this.connectionProvider = connectionProvider;
        this.participantDao = participantDao;
        this.matchService = matchService;
    }

    /**
     * Aggiunge il partecipante alla partita.
     *
     * @param matchId identificativo della partita
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.ParticipantDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ParticipantDTO
     *  
     */
    public ParticipantDTO add(long matchId, Long playerId, Long teamId) {
        Match match = matchService.requireMatch(matchId);
        requireModificabile(match);
        validaCoerenza(match.modalita(), playerId, teamId);

        MatchParticipant creato = connectionProvider.withTransaction(connection -> {
            List<MatchParticipant> alreadyPresent = participantDao.findByMatch(connection, matchId);
            if (alreadyPresent != null) for (MatchParticipant existing : alreadyPresent) {
                if (java.util.Objects.equals(existing.playerId(), playerId)
                        && java.util.Objects.equals(existing.teamId(), teamId)) return existing;
            }
            int ordine = participantDao.prossimoOrdineTurno(connection, matchId);
            long id = participantDao.insert(connection, matchId, playerId, teamId, ordine);
            return new MatchParticipant(id, matchId, playerId, teamId, ordine, 0, false);
        });
        return toDto(creato);
    }

    /**
     * Rimuove il partecipante verificando i vincoli dell'operazione.
     *
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @throws ValidationException se viene rilevato l'errore «Il partecipante non appartiene a questa partita»
     * @throws NotFoundException se viene rilevato l'errore «Partecipante non trovato»
     *  
     */
    public void remove(long matchId, long participantId) {
        Match match = matchService.requireMatch(matchId);
        requireModificabile(match);
        MatchParticipant partecipante = participantDao.findById(participantId)
                .orElseThrow(() -> new NotFoundException("PARTECIPANTE_NON_TROVATO", "Partecipante non trovato"));
        if (partecipante.matchId() != matchId) {
            throw new ValidationException("Il partecipante non appartiene a questa partita");
        }
        participantDao.delete(participantId);
    }

    /**
     * Verifica che la risorsa possa essere modificata.
     *
     * @param match partita da elaborare
     * @throws ConflictException se viene rilevato l'errore «Non è possibile modificare i partecipanti di una partita già avviata»
     * @see it.uniupo.pissir.darts.match.model.Match
     *  
     */
    private void requireModificabile(Match match) {
        if (match.stato() != MatchStatus.CREATA) {
            throw new ConflictException("PARTITA_NON_MODIFICABILE",
                    "Non è possibile modificare i partecipanti di una partita già avviata");
        }
    }

    /**
     * Valida coerenza per il partecipante.
     *
     * @param modalita modalità della partita o del sensore
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @throws ValidationException se viene rilevato l'errore «Specificare esattamente uno tra playerId e teamId»
     * @throws BusinessRuleException se viene rilevato l'errore «Partita individuale: serve un playerId, non un teamId»
     * @see it.uniupo.pissir.darts.match.model.ModalitaPartita
     *  
     */
    private void validaCoerenza(ModalitaPartita modalita, Long playerId, Long teamId) {
        boolean unoSolo = (playerId != null) != (teamId != null);
        if (!unoSolo) {
            throw new ValidationException("Specificare esattamente uno tra playerId e teamId");
        }
        if (modalita == ModalitaPartita.INDIVIDUALE && playerId == null) {
            throw new BusinessRuleException("MODALITA_INCOERENTE", "Partita individuale: serve un playerId, non un teamId");
        }
        if (modalita == ModalitaPartita.SQUADRA && teamId == null) {
            throw new BusinessRuleException("MODALITA_INCOERENTE", "Partita a squadre: serve un teamId, non un playerId");
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param p partecipante alla partita da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.ParticipantDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ParticipantDTO
     *  
     */
    private ParticipantDTO toDto(MatchParticipant p) {
        return new ParticipantDTO(p.id(), p.matchId(), p.playerId(), p.teamId(), p.ordineTurno(),
                p.punteggioCorrente(), p.ritirato());
    }
}
