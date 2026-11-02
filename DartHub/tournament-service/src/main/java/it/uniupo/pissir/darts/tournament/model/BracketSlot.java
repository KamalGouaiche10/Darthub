package it.uniupo.pissir.darts.tournament.model;

/**
 * Riga di bracket_slots (vedi schema.sql): stato del tabellone ELIMINAZIONE.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param tournamentId identificativo del torneo
 * @param turno turno da elaborare
 * @param slot posizione dell'incontro nel tabellone
 * @param participantId identificativo del partecipante
 * @param matchId identificativo della partita
 *  
 */
public record BracketSlot(long tournamentId, int turno, int slot, Long participantId, Long matchId) {
}
