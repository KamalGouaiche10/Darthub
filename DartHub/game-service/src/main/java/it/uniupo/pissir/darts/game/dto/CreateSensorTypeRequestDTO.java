package it.uniupo.pissir.darts.game.dto;

/**
 * Corpo di POST /giochi/{id/sensori}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param formatoDato formato del dato prodotto dal sensore
 * @param modalita modalità della partita o del sensore
 *  
 */
public record CreateSensorTypeRequestDTO(String nome, String formatoDato, String modalita) {
}
