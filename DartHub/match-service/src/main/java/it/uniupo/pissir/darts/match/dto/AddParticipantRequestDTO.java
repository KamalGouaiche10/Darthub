package it.uniupo.pissir.darts.match.dto;

/**
 * Corpo di POST /partite/{id/partecipanti}: esattamente uno tra i due campi.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param playerId identificativo del giocatore
 * @param teamId identificativo della squadra
 *  
 */
public record AddParticipantRequestDTO(Long playerId, Long teamId) {
}
