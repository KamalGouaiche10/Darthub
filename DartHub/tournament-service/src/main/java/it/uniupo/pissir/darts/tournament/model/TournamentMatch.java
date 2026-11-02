package it.uniupo.pissir.darts.tournament.model;

/**
 * Raccoglie i dati di torneo.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param tournamentId identificativo del torneo
 * @param matchId identificativo della partita
 * @param turno turno da elaborare
 * @param slot posizione dell'incontro nel tabellone
 * @param homeParticipantId identificativo del partecipante di casa
 * @param awayParticipantId identificativo del partecipante ospite
 * @param localeId identificativo del locale
 *  
 */
public record TournamentMatch(long tournamentId, long matchId, int turno, Integer slot,
                              Long homeParticipantId, Long awayParticipantId, Long localeId) {
    /**
     * Inizializza {@code TournamentMatch} con i dati e le dipendenze indicati.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param turno turno da elaborare
     * @param slot posizione dell'incontro nel tabellone
     *  
     */
    public TournamentMatch(long tournamentId, long matchId, int turno, Integer slot) {
        this(tournamentId, matchId, turno, slot, null, null, null);
    }
}
