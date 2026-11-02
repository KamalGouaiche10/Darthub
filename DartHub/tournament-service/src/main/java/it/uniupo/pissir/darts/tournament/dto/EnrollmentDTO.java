package it.uniupo.pissir.darts.tournament.dto;

/**
 * Rappresentazione pubblica di un'iscrizione.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della iscrizione al torneo
 * @param tournamentId identificativo del torneo
 * @param participantId identificativo del partecipante
 * @param tipo tipo della risorsa o dell'evento
 * @param dataIscrizione data di iscrizione al torneo
 *  
 */
public record EnrollmentDTO(long id, long tournamentId, long participantId, String tipo, String dataIscrizione) {
}
