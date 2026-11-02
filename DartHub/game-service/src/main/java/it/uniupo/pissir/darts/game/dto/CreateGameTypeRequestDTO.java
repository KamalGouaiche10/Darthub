package it.uniupo.pissir.darts.game.dto;

/**
 * Corpo di POST /giochi.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param descrizione descrizione della risorsa
 * @param motore nome del motore delle regole
 *  
 */
public record CreateGameTypeRequestDTO(String nome, String descrizione, String motore) {
}
