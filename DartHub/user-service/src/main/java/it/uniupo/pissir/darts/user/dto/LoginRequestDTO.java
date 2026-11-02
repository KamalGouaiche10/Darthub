package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di POST /auth/login.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param username nome dell'account
 * @param password password da verificare o registrare
 *  
 */
public record LoginRequestDTO(String username, String password) {
}
