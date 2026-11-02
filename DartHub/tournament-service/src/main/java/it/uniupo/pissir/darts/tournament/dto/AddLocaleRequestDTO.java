package it.uniupo.pissir.darts.tournament.dto;

/**
 * Corpo di POST /tornei/{id/locali}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localeId identificativo del locale
 *  
 */
public record AddLocaleRequestDTO(Long localeId) {
}
