package it.uniupo.pissir.darts.edge.model;

import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;

import java.time.Instant;

/**
 * Riga della tabella buffered_throws.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del tiro da sincronizzare
 * @param localUuid identificativo univoco della partita creata offline
 * @param localParticipantId identificativo del partecipante locale
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param punti punti assegnati al tiro
 * @param numeroTurno numero del turno
 * @param esito esito del tiro o della partita
 * @param annullato indicatore di annullamento del tiro
 * @param timestampSensore istante registrato dal sensore
 * @param sincronizzato indicatore di sincronizzazione con il server
 * @see it.uniupo.pissir.darts.game.model.darts.DartSector
 *  
 */
public record BufferedThrow(long id, String localUuid, long localParticipantId, DartSector settore,
                             Multiplier moltiplicatore, int punti, int numeroTurno, Esito esito, boolean annullato,
                             Instant timestampSensore, boolean sincronizzato) {
}
