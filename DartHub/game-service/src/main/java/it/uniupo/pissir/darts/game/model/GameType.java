package it.uniupo.pissir.darts.game.model;

/**
 * Riga della tabella game_types.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del gioco
 * @param nome nome della risorsa
 * @param descrizione descrizione della risorsa
 * @param motore nome del motore delle regole
 *  
 */
public record GameType(long id, String nome, String descrizione, String motore) {
}
