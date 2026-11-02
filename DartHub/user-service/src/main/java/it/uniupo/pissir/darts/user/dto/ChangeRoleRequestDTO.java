package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di PATCH /users/{id/role}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param role ruolo dell'utente
 *  
 */
public record ChangeRoleRequestDTO(String role) {
}
