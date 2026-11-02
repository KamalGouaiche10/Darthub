package it.uniupo.pissir.darts.tournament.dto;

/**
 * Corpo di PUT /tornei/{id}: solo i campi modificabili dopo la creazione.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param puntiVittoria punti in classifica assegnati per una vittoria
 * @param puntiPareggio punti in classifica assegnati per un pareggio
 * @param puntiSconfitta punti in classifica assegnati per una sconfitta
 *  
 */
public record UpdateTournamentRequestDTO(String nome, Integer puntiVittoria, Integer puntiPareggio,
                                          Integer puntiSconfitta) {
}
