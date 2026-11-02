package it.uniupo.pissir.darts.tournament.dto;

/**
 * Riga della classifica di un torneo (GET /tornei/{id/classifica}).
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 * @param participantName nome pubblico del partecipante
 * @param punti punti assegnati al tiro
 * @param partiteGiocate numero di partite disputate
 * @param vittorie numero di vittorie registrate
 * @param posizione posizione in classifica
 *  
 */
public record StandingDTO(long participantId, String participantName, int punti, int partiteGiocate,
                          int vittorie, Integer posizione) {
}
