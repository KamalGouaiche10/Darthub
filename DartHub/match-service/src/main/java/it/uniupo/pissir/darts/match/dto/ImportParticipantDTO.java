package it.uniupo.pissir.darts.match.dto;

/**
 * Partecipante di una partita nata offline: playerId nullo per gli ospiti non registrati.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param playerId identificativo del giocatore
 * @param nomeVisualizzato nome da mostrare nell'interfaccia
 * @param ordineTurno posizione del partecipante nell'ordine di gioco
 *  
 */
public record ImportParticipantDTO(Long playerId, String nomeVisualizzato, int ordineTurno) {
}
