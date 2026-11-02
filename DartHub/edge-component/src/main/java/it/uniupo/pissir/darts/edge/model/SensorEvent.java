package it.uniupo.pissir.darts.edge.model;

/**
 * Payload di sensori/{localeId/{dartboardId}/tiro}: identico sia che il mittente sia un ESP32 reale sia sensor-simulator.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param timestamp istante dell'evento
 *  
 */
public record SensorEvent(String settore, String moltiplicatore, String timestamp) {
}
