package it.uniupo.pissir.darts.statistics.dto;

/**
 * Raccoglie i dati di global statistics.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param utentiTotali numero totale di utenti conteggiati
 * @param localiTotali numero totale dei locali conteggiati
 * @param partiteTotali numero totale di partite conteggiate
 * @param torneiTotali numero totale di tornei conteggiati
 * @param updatedAt istante dell'ultimo aggiornamento
 *  
 */
public record GlobalStatisticsDTO(int utentiTotali, int localiTotali, int partiteTotali, int torneiTotali,
                                   String updatedAt) {
}
