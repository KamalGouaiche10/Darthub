package it.uniupo.pissir.darts.game.engine;

import it.uniupo.pissir.darts.game.model.darts.DartsRuleEngine;

import java.util.Map;

/**
 * Seleziona il motore associato al gioco configurato.
 *
 * @see java.util.Map
 *  
 */
public final class RuleEngineRegistry {

    private static final Map<String, RuleEngine> MOTORI = Map.of(
            "darts", new DartsRuleEngine());

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private RuleEngineRegistry() {
    }

    /**
     * Seleziona il motore delle regole indicato dalla configurazione.
     *
     * @param motore nome del motore delle regole
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.engine.RuleEngine} prodotto o recuperato
     * @throws IllegalStateException se i dati o lo stato del componente non consentono l'operazione
     * @see it.uniupo.pissir.darts.game.engine.RuleEngine
     *  
     */
    public static RuleEngine resolve(String motore) {
        RuleEngine engine = MOTORI.get(motore);
        if (engine == null) {
            throw new IllegalStateException("Motore di gioco sconosciuto: " + motore);
        }
        return engine;
    }
}
