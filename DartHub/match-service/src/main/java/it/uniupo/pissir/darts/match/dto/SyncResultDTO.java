package it.uniupo.pissir.darts.match.dto;

/**
 * Risposta di POST /partite/sync-offline.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param accettati numero di elementi accettati dalla sincronizzazione
 * @param scartati numero di elementi scartati dalla sincronizzazione
 * @param matchId identificativo della partita
 * @param statoFinale stato ottenuto al termine dell'elaborazione
 *  
 */
public record SyncResultDTO(int accettati, int scartati, long matchId, String statoFinale) {
}
