package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Corpo di POST /partite/import-offline: una partita nata interamente offline.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localUuid identificativo univoco della partita creata offline
 * @param dartboardId identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param gameTypeId identificativo del gioco
 * @param scoringRule regola di punteggio da applicare
 * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
 * @param partecipanti partecipanti associati alla partita
 * @param tiri tiri associati alla partita
 * @see it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO
 *  
 */
public record ImportOfflineRequestDTO(String localUuid, Long dartboardId, Long localeId, Long gameTypeId,
                                       ScoringRuleSnapshotDTO scoringRule, Long clockOffsetMs,
                                       List<ImportParticipantDTO> partecipanti, List<ThrowBatchItemDTO> tiri) {
}
