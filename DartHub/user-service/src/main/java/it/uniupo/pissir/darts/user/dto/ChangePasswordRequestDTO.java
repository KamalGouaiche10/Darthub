package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di PATCH /users/{id/password}: richiede la vecchia password.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param oldPassword password attuale da verificare
 * @param newPassword nuova password dell'account
 *  
 */
public record ChangePasswordRequestDTO(String oldPassword, String newPassword) {
}
