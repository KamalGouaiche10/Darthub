package it.uniupo.pissir.darts.common.dto;

/**
 * Payload del topic eventi/locali/conteggio-cambiato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param localeId identificativo del locale
 * @param conta indicatore di inclusione nel conteggio statistico
 *  
 */
public record LocaleCountChangedEventDTO(String eventId, long localeId, boolean conta) {
}
