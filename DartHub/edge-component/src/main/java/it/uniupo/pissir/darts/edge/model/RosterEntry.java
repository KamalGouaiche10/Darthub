package it.uniupo.pissir.darts.edge.model;

/**
 * Elemento del roster ricevuto con START_MATCH: stessa forma di RosterEntryDTO lato match-service.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 * @param nome nome della risorsa
 * @param ordineTurno posizione del partecipante nell'ordine di gioco
 *  
 */
public record RosterEntry(long participantId, String nome, int ordineTurno) {
}
