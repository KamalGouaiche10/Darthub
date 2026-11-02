package it.uniupo.pissir.darts.tournament.dto;

import java.util.List;

/**
 * Rappresentazione pubblica di un torneo, coi locali associati.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del torneo
 * @param nome nome della risorsa
 * @param gameTypeId identificativo del gioco
 * @param modalita modalità della partita o del sensore
 * @param formula formula del torneo
 * @param puntiVittoria punti in classifica assegnati per una vittoria
 * @param puntiPareggio punti in classifica assegnati per un pareggio
 * @param puntiSconfitta punti in classifica assegnati per una sconfitta
 * @param dataInizio data di inizio del torneo
 * @param dataFine data di fine del torneo
 * @param stato stato da impostare o verificare
 * @param vincitoreParticipantId identificativo del partecipante vincitore
 * @param locali insieme dei locali
 * @param dataInizioEffettiva istante di avvio effettivo del torneo
 * @param dataFineEffettiva istante di conclusione effettiva del torneo
 * @param calendarioEsito esito della generazione del calendario
 * @see java.util.List
 *  
 */
public record TournamentDTO(long id, String nome, long gameTypeId, String modalita, String formula,
                             int puntiVittoria, int puntiPareggio, int puntiSconfitta, String dataInizio,
                             String dataFine, String stato, Long vincitoreParticipantId, List<Long> locali,
                             String dataInizioEffettiva, String dataFineEffettiva, String calendarioEsito) {
}
