package it.uniupo.pissir.darts.statistics.dto;

/**
 * Raccoglie i dati di squadra.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param teamId identificativo della squadra
 * @param partiteGiocate numero di partite disputate
 * @param vittorie numero di vittorie registrate
 * @param percentualeVittorie percentuale delle partite vinte
 * @param puntiTotali totale dei punti registrati
 * @param punteggioMedio media dei punteggi registrati
 * @param updatedAt istante dell'ultimo aggiornamento
 *  
 */
public record TeamStatisticsDTO(long teamId, int partiteGiocate, int vittorie, double percentualeVittorie,
                                 int puntiTotali, double punteggioMedio, String updatedAt) {
}
