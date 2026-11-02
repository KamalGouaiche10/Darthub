package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di POST /locali/{id/bersagli}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param identificativoLocale identificativo del bersaglio nel locale
 * @param gameTypeId identificativo del gioco
 *  
 */
public record CreateDartboardRequestDTO(String identificativoLocale, Long gameTypeId) {
}
