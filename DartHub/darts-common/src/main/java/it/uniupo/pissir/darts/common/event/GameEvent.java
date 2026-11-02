package it.uniupo.pissir.darts.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * Evento di gioco generico: sostituisce il vecchio ThrowEventDTO darts-specifico (settore/moltiplicatore) come formato unico usato da sensori, edge, RuleEngine, match-service e bus MQTT — per QUALSIASI tipo di gioco.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param matchId identificativo della partita
 * @param participantId identificativo del partecipante
 * @param playerId identificativo del giocatore
 * @param teamId identificativo della squadra
 * @param tipo tipo della risorsa o dell'evento
 * @param payload contenuto del messaggio
 * @param punti punti assegnati al tiro
 * @param timestampSensore istante registrato dal sensore
 * @param origine origine dei dati della partita o del tiro
 * @param esito esito del tiro o della partita
 * @see java.util.Map
 *  
 */
public record GameEvent(String eventId, Long matchId, Long participantId, Long playerId, Long teamId, String tipo,
                        Map<String, Object> payload, Integer punti, Instant timestampSensore, String origine,
                        String esito) {

    /**
     * Restituisce il contenuto originale della risposta.
     *
     * @param tipo tipo della risorsa o dell'evento
     * @param payload contenuto del messaggio
     * @param timestampSensore istante registrato dal sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.event.GameEvent} prodotto o recuperato
     * @see java.util.Map
     *  
     */
    public static GameEvent grezzo(String tipo, Map<String, Object> payload, Instant timestampSensore) {
        return new GameEvent(null, null, null, null, null, tipo, payload, null, timestampSensore, null, null);
    }

    /**
     * Completa l'evento con la partita e il partecipante indicati.
     *
     * @param matchId identificativo della partita
     * @param participantId identificativo del partecipante
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.event.GameEvent} prodotto o recuperato
     *  
     */
    public GameEvent conMatchEPartecipante(long matchId, long participantId) {
        return new GameEvent(eventId, matchId, participantId, playerId, teamId, tipo, payload, punti,
                timestampSensore, origine, esito);
    }

    /**
     * Compone l'evento di gioco con l'esito già calcolato.
     *
     * @param eventId identificativo dell'evento
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @param punti punti assegnati al tiro
     * @param origine origine dei dati della partita o del tiro
     * @param esito esito del tiro o della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.event.GameEvent} prodotto o recuperato
     *  
     */
    public GameEvent valutato(String eventId, Long playerId, Long teamId, int punti, String origine, String esito) {
        return new GameEvent(eventId, matchId, participantId, playerId, teamId, tipo, payload, punti,
                timestampSensore, origine, esito);
    }
}
