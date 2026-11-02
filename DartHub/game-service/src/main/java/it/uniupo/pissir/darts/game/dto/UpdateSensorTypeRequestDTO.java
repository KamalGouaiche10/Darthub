package it.uniupo.pissir.darts.game.dto;

/**
 * Corpo di PUT /sensori/{id}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param formatoDato formato del dato prodotto dal sensore
 * @param modalita modalità della partita o del sensore
 *  
 */
public record UpdateSensorTypeRequestDTO(String nome, String formatoDato, String modalita) {
}
