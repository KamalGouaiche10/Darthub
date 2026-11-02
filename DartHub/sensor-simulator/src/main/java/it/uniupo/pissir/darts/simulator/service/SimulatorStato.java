package it.uniupo.pissir.darts.simulator.service;

import it.uniupo.pissir.darts.simulator.generator.Scenario;

/**
 * Istantanea dello stato del motore di simulazione, esposta da GET /simulatore/stato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 * @param scenario scenario di generazione dei tiri
 * @param intervalloMs intervallo fra i tiri in millisecondi
 * @param inEsecuzione indicatore di generazione automatica dei tiri
 * @param connesso indicatore di connessione al broker
 * @param batteria livello della batteria del sensore
 * @param matchId identificativo della partita
 * @see it.uniupo.pissir.darts.simulator.generator.Scenario
 *  
 */
public record SimulatorStato(long localeId, long dartboardId, Scenario scenario, long intervalloMs,
                              boolean inEsecuzione, boolean connesso, int batteria, Long matchId) {
}
