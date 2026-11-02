package it.uniupo.pissir.darts.statistics.dto;

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
public record GameStatisticsDTO(long gameTypeId, int partiteTotali, double mediaGiocatoriPerPartita) {
}
