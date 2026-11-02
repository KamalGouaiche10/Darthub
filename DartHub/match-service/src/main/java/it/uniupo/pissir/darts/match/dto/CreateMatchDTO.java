package it.uniupo.pissir.darts.match.dto;

/**
 * Corpo di POST /partite.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param dartboardId identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param gameTypeId identificativo del gioco
 * @param scoringRuleId identificativo della regola di punteggio
 * @param modalita modalità della partita o del sensore
 * @param idempotencyKey chiave di riconoscimento della richiesta già elaborata
 *  
 */
public record CreateMatchDTO(Long dartboardId, Long localeId, Long gameTypeId, Long scoringRuleId, String modalita,
                             String idempotencyKey) {
}
