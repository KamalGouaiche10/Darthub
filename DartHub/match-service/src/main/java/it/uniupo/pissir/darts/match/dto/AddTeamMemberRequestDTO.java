package it.uniupo.pissir.darts.match.dto;

/**
 * Corpo di POST /squadre/{id/membri}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param playerId identificativo del giocatore
 *  
 */
public record AddTeamMemberRequestDTO(Long playerId) {
}
