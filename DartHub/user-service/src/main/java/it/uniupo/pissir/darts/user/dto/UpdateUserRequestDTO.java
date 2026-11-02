package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di PUT /users/{id}: email, nickname, avatar.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param email indirizzo email dell'account
 * @param nickname nome pubblico del giocatore
 * @param avatarUrl indirizzo dell'immagine del profilo
 * @param firstName nome anagrafico dell'utente
 * @param lastName cognome dell'utente
 *  
 */
public record UpdateUserRequestDTO(String email, String nickname, String avatarUrl,
                                   String firstName, String lastName) {
}
