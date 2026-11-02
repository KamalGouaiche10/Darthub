package it.uniupo.pissir.darts.statistics.model;

import java.time.Instant;

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
 * @see java.time.Instant
 *  
 */
public record PlayerStatistics(long playerId, int partiteGiocate, int vittorie, double percentualeVittorie,
                                int puntiTotali, double punteggioMedio, int migliorTiro, Instant updatedAt) {
}
