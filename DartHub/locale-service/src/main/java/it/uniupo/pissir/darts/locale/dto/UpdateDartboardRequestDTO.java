package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di PUT /bersagli/{id}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param identificativoLocale identificativo del bersaglio nel locale
 * @param gameTypeId identificativo del gioco
 *  
 */
public record UpdateDartboardRequestDTO(String identificativoLocale, Long gameTypeId) {
}
