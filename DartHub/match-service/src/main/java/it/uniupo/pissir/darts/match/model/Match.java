package it.uniupo.pissir.darts.match.model;

import java.time.Instant;

/**
 * Riga della tabella matches.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della partita
 * @param dartboardId identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param gameTypeId identificativo del gioco
 * @param scoringRuleId identificativo della regola di punteggio
 * @param modalita modalità della partita o del sensore
 * @param stato stato da impostare o verificare
 * @param createdBy identificativo dell'utente che ha creato la risorsa
 * @param localUuid identificativo univoco della partita creata offline
 * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
 * @param dataInizio data di inizio del torneo
 * @param dataFine data di fine del torneo
 * @see it.uniupo.pissir.darts.match.model.ModalitaPartita
 *  
 */
public record Match(long id, long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                     ModalitaPartita modalita, MatchStatus stato, long createdBy, String localUuid,
                     long clockOffsetMs, Instant dataInizio, Instant dataFine) {
}
