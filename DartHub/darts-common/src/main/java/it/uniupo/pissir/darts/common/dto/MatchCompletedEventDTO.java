package it.uniupo.pissir.darts.common.dto;

import java.util.List;

/**
 * Payload del topic eventi/partite/completata.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param matchId identificativo della partita
 * @param gameTypeId identificativo del gioco
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 * @param modalita modalità della partita o del sensore
 * @param risultati risultati dei partecipanti alla partita
 * @see java.util.List
 *  
 */
public record MatchCompletedEventDTO(String eventId, long matchId, long gameTypeId, long localeId, long dartboardId,
                                      String modalita, List<Risultato> risultati) {

    /**
     * Raccoglie i dati di risultato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @param punteggioFinale punteggio alla fine della partita
     * @param posizione posizione in classifica
     * @param vincitore partecipante vincitore
     *  
     */
    public record Risultato(Long playerId, Long teamId, int punteggioFinale, int posizione, boolean vincitore) {
    }
}
