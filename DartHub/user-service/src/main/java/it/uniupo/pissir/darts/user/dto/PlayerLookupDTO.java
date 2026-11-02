package it.uniupo.pissir.darts.user.dto;

/**
 * Proiezione minima restituita da GET /users/lookup?username=...: solo quanto serve ad aggiungere un avversario in "Nuova partita".
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del giocatore
 * @param username nome dell'account
 * @param nickname nome pubblico del giocatore
 *  
 */
public record PlayerLookupDTO(long id, String username, String nickname) {
}
