package it.uniupo.pissir.darts.tournament.dto;

/**
 * Corpo di POST /tornei.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param gameTypeId identificativo del gioco
 * @param modalita modalità della partita o del sensore
 * @param formula formula del torneo
 * @param puntiVittoria punti in classifica assegnati per una vittoria
 * @param puntiPareggio punti in classifica assegnati per un pareggio
 * @param puntiSconfitta punti in classifica assegnati per una sconfitta
 * @param dataInizio data di inizio del torneo
 * @param dataFine data di fine del torneo
 *  
 */
public record CreateTournamentRequestDTO(String nome, Long gameTypeId, String modalita, String formula,
                                          Integer puntiVittoria, Integer puntiPareggio, Integer puntiSconfitta,
                                          String dataInizio, String dataFine) {
}
