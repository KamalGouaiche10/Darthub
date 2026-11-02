package it.uniupo.pissir.darts.edge.model;

/**
 * Raccoglie i dati di snapshot della regola di punteggio.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param variante nome della variante del gioco
 * @param direzione direzione di variazione del punteggio
 * @param punteggioIniziale punteggio iniziale previsto dalla regola
 * @param punteggioObiettivo punteggio da raggiungere per vincere
 * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
 * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
 * @param maxTiriPerTurno numero massimo di tiri per turno
 *  
 */
public record ScoringRuleSnapshot(String variante, String direzione, int punteggioIniziale,
                                   int punteggioObiettivo, boolean chiusuraDouble, boolean bustAnnullaTurno,
                                   int maxTiriPerTurno) {
}
