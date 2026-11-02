package it.uniupo.pissir.darts.tournament.model;

/**
 * Raccoglie i dati di classifica.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param tournamentId identificativo del torneo
 * @param participantId identificativo del partecipante
 * @param punti punti assegnati al tiro
 * @param partiteGiocate numero di partite disputate
 * @param vittorie numero di vittorie registrate
 * @param posizione posizione in classifica
 *  
 */
public record Standing(long tournamentId, long participantId, int punti, int partiteGiocate, int vittorie,
                        Integer posizione) {
}
