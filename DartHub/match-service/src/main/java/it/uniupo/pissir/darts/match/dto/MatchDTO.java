package it.uniupo.pissir.darts.match.dto;

/**
 * Rappresentazione pubblica di una partita.
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
 * @param sensorTournament indicatore di partita del torneo gestita dal sensore
 *  
 */
public record MatchDTO(long id, long dartboardId, long localeId, long gameTypeId, long scoringRuleId,
                        String modalita, String stato, long createdBy, String localUuid, long clockOffsetMs,
                        String dataInizio, String dataFine, boolean sensorTournament) {
}
