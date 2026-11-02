package it.uniupo.pissir.darts.common.dto;

import java.time.Instant;

/**
 * Payload del topic darts/{localeId/{dartboardId}/tiro}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param eventId identificativo dell'evento
 * @param matchId identificativo della partita
 * @param participantId identificativo del partecipante
 * @param playerId identificativo del giocatore
 * @param teamId identificativo della squadra
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param punti punti assegnati al tiro
 * @param timestampSensore istante registrato dal sensore
 * @param origine origine dei dati della partita o del tiro
 * @param esito esito del tiro o della partita
 * @see java.time.Instant
 *  
 */
public record ThrowEventDTO(String eventId, long matchId, long participantId, Long playerId, Long teamId,
                             String settore, String moltiplicatore, int punti, Instant timestampSensore,
                             String origine, String esito) {
}
