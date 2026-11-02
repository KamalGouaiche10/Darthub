package it.uniupo.pissir.darts.tournament.dto;

/**
 * Corpo di POST /tornei/{id/iscrizioni}: participantId è un playerId o un teamId.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 *  
 */
public record EnrollRequestDTO(Long participantId) {
}
