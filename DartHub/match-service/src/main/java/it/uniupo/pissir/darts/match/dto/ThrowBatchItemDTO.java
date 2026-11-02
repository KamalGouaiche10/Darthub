package it.uniupo.pissir.darts.match.dto;

/**
 * Elemento di un batch offline (sync-offline/import-offline).
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param participantId identificativo del partecipante
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param timestampSensore istante registrato dal sensore
 *  
 */
public record ThrowBatchItemDTO(Long participantId, String settore, String moltiplicatore, String timestampSensore) {
}
