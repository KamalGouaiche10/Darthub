package it.uniupo.pissir.darts.game.dto;

/**
 * Corpo di PUT /regole/{id}.
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
 * @param parametriJson parametri aggiuntivi della regola in JSON
 *  
 */
public record UpdateScoringRuleRequestDTO(String variante, String direzione, Integer punteggioIniziale,
                                           Integer punteggioObiettivo, Boolean chiusuraDouble,
                                           Boolean bustAnnullaTurno, Integer maxTiriPerTurno, String parametriJson) {
}
