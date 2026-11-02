package it.uniupo.pissir.darts.edge.model;

import java.util.List;

/**
 * Payload di darts/{localeId/{dartboardId}/comandi}: stessa forma di EdgeCommandDTO lato match-service.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param comando comando da applicare al dispositivo
 * @param matchId identificativo della partita
 * @param scoringRule regola di punteggio da applicare
 * @param roster formazione dei partecipanti della partita
 * @see it.uniupo.pissir.darts.edge.model.ScoringRuleSnapshot
 *  
 */
public record RemoteCommand(String comando, long matchId, ScoringRuleSnapshot scoringRule, List<RosterEntry> roster) {
}
