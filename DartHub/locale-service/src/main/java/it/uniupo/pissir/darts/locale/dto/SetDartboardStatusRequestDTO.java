package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di PATCH /bersagli/{id/stato}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param stato stato da impostare o verificare
 *  
 */
public record SetDartboardStatusRequestDTO(String stato) {
}
