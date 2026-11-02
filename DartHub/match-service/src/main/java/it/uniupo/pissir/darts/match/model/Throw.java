package it.uniupo.pissir.darts.match.model;

import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;

import java.time.Instant;

/**
 * Riga della tabella throws.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del tiro
 * @param matchId identificativo della partita
 * @param participantId identificativo del partecipante
 * @param tipo tipo della risorsa o dell'evento
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param payloadJson contenuto del messaggio in JSON
 * @param teamId identificativo della squadra
 * @param punti punti assegnati al tiro
 * @param numeroTurno numero del turno
 * @param esito esito del tiro o della partita
 * @param annullato indicatore di annullamento del tiro
 * @param timestampSensore istante registrato dal sensore
 * @param timestampRicezione istante di ricezione dell'evento
 * @param origine origine dei dati della partita o del tiro
 * @param dartboardId identificativo del bersaglio
 * @see it.uniupo.pissir.darts.game.model.darts.DartSector
 *  
 */
public record Throw(long id, long matchId, long participantId, String tipo, DartSector settore,
                     Multiplier moltiplicatore, String payloadJson, Long teamId, int punti, int numeroTurno,
                     Esito esito, boolean annullato, Instant timestampSensore, Instant timestampRicezione,
                     ThrowOrigin origine, long dartboardId) {
}
