package it.uniupo.pissir.darts.statistics.model;

/**
 * Raccoglie i dati di gioco statistics.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param gameTypeId identificativo del gioco
 * @param partiteTotali numero totale di partite conteggiate
 * @param mediaGiocatoriPerPartita numero medio di giocatori per partita
 *  
 */
public record GameStatistics(long gameTypeId, int partiteTotali, double mediaGiocatoriPerPartita) {
}
