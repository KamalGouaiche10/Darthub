package it.uniupo.pissir.darts.tournament.model;

/**
 * Raccoglie i dati di torneo.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param tournamentId identificativo del torneo
 * @param localeId identificativo del locale
 *  
 */
public record TournamentLocale(long tournamentId, long localeId) {
}
