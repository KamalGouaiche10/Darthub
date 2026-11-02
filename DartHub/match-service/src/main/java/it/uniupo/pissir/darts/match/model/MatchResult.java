package it.uniupo.pissir.darts.match.model;

/**
 * Riga della tabella match_results.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del risultato della partita
 * @param matchId identificativo della partita
 * @param participantId identificativo del partecipante
 * @param punteggioFinale punteggio alla fine della partita
 * @param posizione posizione in classifica
 * @param vincitore partecipante vincitore
 *  
 */
public record MatchResult(long id, long matchId, long participantId, int punteggioFinale, int posizione,
                           boolean vincitore) {
}
