package it.uniupo.pissir.darts.user.dto;

/**
 * Rappresentazione pubblica di un utente: non contiene mai password, hash o salt.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo dell'utente
 * @param username nome dell'account
 * @param email indirizzo email dell'account
 * @param role ruolo dell'utente
 * @param enabled indicatore di abilitazione dell'account
 * @param approvalStatus stato di approvazione
 * @param nickname nome pubblico del giocatore
 * @param avatarUrl indirizzo dell'immagine del profilo
 * @param createdAt istante di creazione
 * @param firstName nome anagrafico dell'utente
 * @param lastName cognome dell'utente
 *  
 */
public record UserDTO(long id, String username, String email, String role, boolean enabled,
                       String approvalStatus, String nickname, String avatarUrl, String createdAt,
                       String firstName, String lastName) {
}
