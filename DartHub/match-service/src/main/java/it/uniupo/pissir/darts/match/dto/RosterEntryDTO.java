package it.uniupo.pissir.darts.match.dto;

/**
 * Elemento del roster (participantId, nome visualizzato, ordine turno): indispensabile all'edge per attribuire i tiri.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 * @param nome nome della risorsa
 * @param ordineTurno posizione del partecipante nell'ordine di gioco
 * @param playerId identificativo del giocatore
 * @param teamId identificativo della squadra
 *  
 */
public record RosterEntryDTO(long participantId, String nome, int ordineTurno, Long playerId, Long teamId) {
}
