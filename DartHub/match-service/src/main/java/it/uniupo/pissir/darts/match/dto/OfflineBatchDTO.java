package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Corpo di POST /partite/sync-offline: soli tiri di una partita già esistente sul server.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param dartboardId identificativo del bersaglio
 * @param matchId identificativo della partita
 * @param clockOffsetMs scarto fra orologio del sensore e del server, in millisecondi
 * @param tiri tiri associati alla partita
 * @see java.util.List
 *  
 */
public record OfflineBatchDTO(Long dartboardId, Long matchId, Long clockOffsetMs, List<ThrowBatchItemDTO> tiri) {
}
