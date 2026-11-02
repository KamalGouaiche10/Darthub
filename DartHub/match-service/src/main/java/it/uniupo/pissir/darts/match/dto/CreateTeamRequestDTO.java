package it.uniupo.pissir.darts.match.dto;

/**
 * Corpo di POST /squadre.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 *  
 */
public record CreateTeamRequestDTO(String nome) {
}
