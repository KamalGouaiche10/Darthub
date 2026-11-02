package it.uniupo.pissir.darts.tournament.model;

import java.time.Instant;

/**
 * Raccoglie i dati di iscrizione al torneo.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della iscrizione al torneo
 * @param tournamentId identificativo del torneo
 * @param participantId identificativo del partecipante
 * @param tipo tipo della risorsa o dell'evento
 * @param dataIscrizione data di iscrizione al torneo
 * @see it.uniupo.pissir.darts.tournament.model.TipoIscrizione
 *  
 */
public record Enrollment(long id, long tournamentId, long participantId, TipoIscrizione tipo, Instant dataIscrizione) {
}
