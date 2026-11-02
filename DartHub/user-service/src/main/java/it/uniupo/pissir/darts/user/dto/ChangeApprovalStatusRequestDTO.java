package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di PATCH /users/{id/approvazione}: stato è APPROVATO o RIFIUTATO.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param stato stato da impostare o verificare
 *  
 */
public record ChangeApprovalStatusRequestDTO(String stato) {
}
