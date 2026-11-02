package it.uniupo.pissir.darts.statistics.dto;

/**
 * Raccoglie i dati di locale.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 * @param numeroPartite numero di partite conteggiate
 * @param giocatoriDistinti numero di giocatori distinti
 * @param ultimoUtilizzo istante dell'ultimo utilizzo
 *  
 */
public record LocaleStatisticsDTO(long localeId, long dartboardId, int numeroPartite, int giocatoriDistinti,
                                   String ultimoUtilizzo) {
}
