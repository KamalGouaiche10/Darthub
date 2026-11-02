package it.uniupo.pissir.darts.tournament.model;

import java.time.Instant;

/**
 * Raccoglie i dati di torneo.
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
 * @param dataInizioEffettiva istante di avvio effettivo del torneo
 * @param dataFineEffettiva istante di conclusione effettiva del torneo
 * @see it.uniupo.pissir.darts.tournament.model.ModalitaTorneo
 *  
 */
public record Tournament(long id, String nome, long gameTypeId, ModalitaTorneo modalita, FormulaTorneo formula,
                          int puntiVittoria, int puntiPareggio, int puntiSconfitta, Instant dataInizio,
                          Instant dataFine, TournamentStatus stato, Long vincitoreParticipantId,
                          Instant dataInizioEffettiva, Instant dataFineEffettiva) {
    /**
     * Inizializza {@code Tournament} con i dati e le dipendenze indicati.
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
     * @see it.uniupo.pissir.darts.tournament.model.ModalitaTorneo
     *  
     */
    public Tournament(long id, String nome, long gameTypeId, ModalitaTorneo modalita, FormulaTorneo formula,
                      int puntiVittoria, int puntiPareggio, int puntiSconfitta, Instant dataInizio,
                      Instant dataFine, TournamentStatus stato, Long vincitoreParticipantId) {
        this(id, nome, gameTypeId, modalita, formula, puntiVittoria, puntiPareggio, puntiSconfitta,
                dataInizio, dataFine, stato, vincitoreParticipantId, null, null);
    }
}
