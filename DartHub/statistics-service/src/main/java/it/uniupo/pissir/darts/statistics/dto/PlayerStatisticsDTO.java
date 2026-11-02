package it.uniupo.pissir.darts.statistics.dto;

/**
 * Raccoglie i dati di giocatore.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param playerId identificativo del giocatore
 * @param partiteGiocate numero di partite disputate
 * @param vittorie numero di vittorie registrate
 * @param percentualeVittorie percentuale delle partite vinte
 * @param puntiTotali totale dei punti registrati
 * @param punteggioMedio media dei punteggi registrati
 * @param migliorTiro punteggio del miglior tiro registrato
 * @param updatedAt istante dell'ultimo aggiornamento
 *  
 */
public record PlayerStatisticsDTO(long playerId, int partiteGiocate, int vittorie, double percentualeVittorie,
                                   int puntiTotali, double punteggioMedio, int migliorTiro, String updatedAt) {
}
