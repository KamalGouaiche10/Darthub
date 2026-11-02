package it.uniupo.pissir.darts.common.dto;

/**
 * Payload del topic eventi/utenti/conteggio-cambiato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param userId identificativo dell'utente
 * @param conta indicatore di inclusione nel conteggio statistico
 *  
 */
public record UserCountChangedEventDTO(String eventId, long userId, boolean conta) {
}
