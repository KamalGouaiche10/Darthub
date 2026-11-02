package it.uniupo.pissir.darts.game.dto;

/**
 * Rappresentazione pubblica di un sensor_type.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del tipo di sensore
 * @param gameTypeId identificativo del gioco
 * @param nome nome della risorsa
 * @param formatoDato formato del dato prodotto dal sensore
 * @param modalita modalità della partita o del sensore
 *  
 */
public record SensorTypeDTO(long id, long gameTypeId, String nome, String formatoDato, String modalita) {
}
