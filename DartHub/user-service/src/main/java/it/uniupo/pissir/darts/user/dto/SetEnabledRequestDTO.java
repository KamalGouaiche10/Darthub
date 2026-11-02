package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di PATCH /users/{id/enabled}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param enabled indicatore di abilitazione dell'account
 *  
 */
public record SetEnabledRequestDTO(boolean enabled) {
}
