package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Payload di darts/{localeId/{dartboardId}/punteggio}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param matchId identificativo della partita
 * @param punteggi punteggi dei partecipanti
 * @param giocatoreAttivo giocatore a cui spetta il turno
 * @param esitoUltimoTiro esito dell'ultimo tiro elaborato
 * @see java.util.List
 *  
 */
public record ScorePushDTO(long matchId, List<ScoreDTO.ParticipantScoreDTO> punteggi, Long giocatoreAttivo,
                            String esitoUltimoTiro) {
}
