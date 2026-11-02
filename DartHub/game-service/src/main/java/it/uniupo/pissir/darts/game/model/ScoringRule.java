package it.uniupo.pissir.darts.game.model;

/**
 * Riga della tabella scoring_rules: passata al RuleEngine risolto per il motore del gioco.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della regola di punteggio
 * @param gameTypeId identificativo del gioco
 * @param variante nome della variante del gioco
 * @param direzione direzione di variazione del punteggio
 * @param punteggioIniziale punteggio iniziale previsto dalla regola
 * @param punteggioObiettivo punteggio da raggiungere per vincere
 * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
 * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
 * @param maxTiriPerTurno numero massimo di tiri per turno
 * @param parametriJson parametri aggiuntivi della regola in JSON
 * @see it.uniupo.pissir.darts.game.model.Direzione
 *  
 */
public record ScoringRule(long id, long gameTypeId, String variante, Direzione direzione, int punteggioIniziale,
                           int punteggioObiettivo, boolean chiusuraDouble, boolean bustAnnullaTurno,
                           int maxTiriPerTurno, String parametriJson) {

}
