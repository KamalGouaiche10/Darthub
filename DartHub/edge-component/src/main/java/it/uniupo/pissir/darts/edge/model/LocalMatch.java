package it.uniupo.pissir.darts.edge.model;

import java.time.Instant;

/**
 * Riga della tabella local_matches.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localUuid identificativo univoco della partita creata offline
 * @param serverId identificativo assegnato dal server centrale
 * @param dartboardId identificativo del bersaglio
 * @param scoringRuleJson regola di punteggio in JSON
 * @param origine origine dei dati della partita o del tiro
 * @param stato stato da impostare o verificare
 * @param importata indicatore di importazione della partita sul server
 * @param dataInizio data di inizio del torneo
 * @param dataFine data di fine del torneo
 * @see it.uniupo.pissir.darts.edge.model.LocalMatchOrigin
 *  
 */
public record LocalMatch(String localUuid, Long serverId, long dartboardId, String scoringRuleJson,
                          LocalMatchOrigin origine, LocalMatchStatus stato, boolean importata,
                          Instant dataInizio, Instant dataFine) {
}
