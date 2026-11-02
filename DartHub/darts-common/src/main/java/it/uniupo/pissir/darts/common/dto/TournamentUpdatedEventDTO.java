package it.uniupo.pissir.darts.common.dto;

import java.util.List;

/**
 * Payload del topic eventi/tornei/aggiornato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param tournamentId identificativo del torneo
 * @param classifica classifica dei partecipanti
 * @see java.util.List
 *  
 */
public record TournamentUpdatedEventDTO(String eventId, long tournamentId, List<Standing> classifica) {

    /**
     * Raccoglie i dati di classifica.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param punti punti assegnati al tiro
     * @param partiteGiocate numero di partite disputate
     * @param vittorie numero di vittorie registrate
     * @param posizione posizione in classifica
     *  
     */
    public record Standing(long participantId, int punti, int partiteGiocate, int vittorie, Integer posizione) {
    }
}
