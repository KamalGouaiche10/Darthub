package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di POST /auth/register.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param username nome dell'account
 * @param email indirizzo email dell'account
 * @param password password da verificare o registrare
 * @param nickname nome pubblico del giocatore
 * @param role ruolo dell'utente
 * @param firstName nome anagrafico dell'utente
 * @param lastName cognome dell'utente
 *  
 */
public record RegisterRequestDTO(String username, String email, String password, String nickname,
                                 String role, String firstName, String lastName) {
}
