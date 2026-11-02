package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di PATCH /locali/{id/stato}: stato è APPROVATO o RIFIUTATO.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param stato stato da impostare o verificare
 *  
 */
public record ChangeLocaleStatusRequestDTO(String stato) {
}
