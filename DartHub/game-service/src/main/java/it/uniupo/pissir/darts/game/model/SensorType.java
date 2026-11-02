package it.uniupo.pissir.darts.game.model;

/**
 * Riga della tabella sensor_types.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del tipo di sensore
 * @param gameTypeId identificativo del gioco
 * @param nome nome della risorsa
 * @param formatoDato formato del dato prodotto dal sensore
 * @param modalita modalità della partita o del sensore
 * @see it.uniupo.pissir.darts.game.model.SensorModalita
 *  
 */
public record SensorType(long id, long gameTypeId, String nome, String formatoDato, SensorModalita modalita) {
}
