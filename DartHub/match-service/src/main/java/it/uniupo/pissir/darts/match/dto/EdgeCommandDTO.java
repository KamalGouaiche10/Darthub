package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Payload di darts/{localeId/{dartboardId}/comandi}: il roster è indispensabile su START_MATCH, senza di esso l'edge non può attribuire i tiri.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param comando comando da applicare al dispositivo
 * @param matchId identificativo della partita
 * @param scoringRule regola di punteggio da applicare
 * @param roster formazione dei partecipanti della partita
 * @see it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO
 *  
 */
public record EdgeCommandDTO(String comando, long matchId, ScoringRuleSnapshotDTO scoringRule,
                              List<RosterEntryDTO> roster) {

    /**
     * Avvia la partita con la regola e i partecipanti assegnati.
     *
     * @param matchId identificativo della partita
     * @param scoringRule regola di punteggio da applicare
     * @param roster formazione dei partecipanti della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.EdgeCommandDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.ScoringRuleSnapshotDTO
     *  
     */
    public static EdgeCommandDTO startMatch(long matchId, ScoringRuleSnapshotDTO scoringRule, List<RosterEntryDTO> roster) {
        return new EdgeCommandDTO("START_MATCH", matchId, scoringRule, roster);
    }

    /**
     * Chiude la partita attiva.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.EdgeCommandDTO} prodotto o recuperato
     *  
     */
    public static EdgeCommandDTO endMatch(long matchId) {
        return new EdgeCommandDTO("END_MATCH", matchId, null, null);
    }
}
