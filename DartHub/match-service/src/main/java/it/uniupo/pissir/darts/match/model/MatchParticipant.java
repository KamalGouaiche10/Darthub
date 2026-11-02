package it.uniupo.pissir.darts.match.model;

/**
 * Riga della tabella match_participants.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del partecipante alla partita
 * @param matchId identificativo della partita
 * @param playerId identificativo del giocatore
 * @param teamId identificativo della squadra
 * @param ordineTurno posizione del partecipante nell'ordine di gioco
 * @param punteggioCorrente punteggio attuale del partecipante
 * @param ritirato indicatore di ritiro del partecipante
 *  
 */
public record MatchParticipant(long id, long matchId, Long playerId, Long teamId, int ordineTurno,
                                int punteggioCorrente, boolean ritirato) {

}
