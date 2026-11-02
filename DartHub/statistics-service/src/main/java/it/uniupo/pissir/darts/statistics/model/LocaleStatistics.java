package it.uniupo.pissir.darts.statistics.model;

import java.time.Instant;

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
 * @see java.time.Instant
 *  
 */
public record LocaleStatistics(long localeId, long dartboardId, int numeroPartite, int giocatoriDistinti,
                                Instant ultimoUtilizzo) {
}
