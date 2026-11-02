package it.uniupo.pissir.darts.game.engine;

import java.util.Map;

/**
 * Risultato di RuleEngine#applica: generalizza ThrowOutcome.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param esito esito del tiro o della partita
 * @param nuovoStato stato dopo l'applicazione dell'evento
 * @param vincitoreParticipantId identificativo del partecipante vincitore
 * @param motivo motivazione associata all'esito
 * @param extra dati aggiuntivi dell'evento
 * @see it.uniupo.pissir.darts.game.engine.Esito
 *  
 */
public record RuleOutcome(Esito esito, MatchState nuovoStato, Long vincitoreParticipantId,
                           String motivo, Map<String, Object> extra) {
}
