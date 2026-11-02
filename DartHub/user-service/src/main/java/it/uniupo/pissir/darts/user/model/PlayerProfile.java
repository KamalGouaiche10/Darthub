package it.uniupo.pissir.darts.user.model;

/**
 * Riga della tabella player_profiles.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param userId identificativo dell'utente
 * @param nickname nome pubblico del giocatore
 * @param avatarUrl indirizzo dell'immagine del profilo
 *  
 */
public record PlayerProfile(long userId, String nickname, String avatarUrl) {
}
