package it.uniupo.pissir.darts.game.dto;

/**
 * Corpo di PUT /giochi/{id}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param descrizione descrizione della risorsa
 * @param motore nome del motore delle regole
 *  
 */
public record UpdateGameTypeRequestDTO(String nome, String descrizione, String motore) {
}
