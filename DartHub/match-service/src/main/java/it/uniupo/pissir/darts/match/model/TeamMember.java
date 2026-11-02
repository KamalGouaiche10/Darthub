package it.uniupo.pissir.darts.match.model;

/**
 * Riga della tabella team_members.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param teamId identificativo della squadra
 * @param playerId identificativo del giocatore
 *  
 */
public record TeamMember(long teamId, long playerId) {
}
