package it.uniupo.pissir.darts.edge.model;

/**
 * Riga della tabella local_participants.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del partecipante locale
 * @param localUuid identificativo univoco della partita creata offline
 * @param serverParticipantId identificativo del partecipante sul server
 * @param playerId identificativo del giocatore
 * @param nomeVisualizzato nome da mostrare nell'interfaccia
 * @param ordineTurno posizione del partecipante nell'ordine di gioco
 * @param punteggioCorrente punteggio attuale del partecipante
 *  
 */
public record LocalParticipant(long id, String localUuid, Long serverParticipantId, Long playerId,
                                String nomeVisualizzato, int ordineTurno, int punteggioCorrente) {
}
