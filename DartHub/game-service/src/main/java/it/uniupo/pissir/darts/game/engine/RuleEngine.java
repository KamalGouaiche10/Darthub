package it.uniupo.pissir.darts.game.engine;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.game.model.ScoringRule;

/**
 * Definisce il contratto dei motori delle regole di gioco. 
 */
public interface RuleEngine {

    /**
     * Applica l'evento alla regola e allo stato corrente della partita.
     *
     * @param regola regola di punteggio da applicare
     * @param statoCorrente stato corrente
     * @param evento evento da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.engine.RuleOutcome} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.engine.RuleOutcome
     *  
     */
    RuleOutcome applica(ScoringRule regola, MatchState statoCorrente, GameEvent evento);
}
