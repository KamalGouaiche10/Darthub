package it.uniupo.pissir.darts.tournament.dto;

/**
 * Corpo di POST /tornei/{id/partite}: aggiunta manuale di una partita già esistente.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param matchId identificativo della partita
 *  
 */
public record AddMatchRequestDTO(Long matchId) {
}
