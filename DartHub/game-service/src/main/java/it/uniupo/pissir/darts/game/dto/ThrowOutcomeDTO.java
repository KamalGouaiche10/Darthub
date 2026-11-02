package it.uniupo.pissir.darts.game.dto;

/**
 * Rappresentazione REST di ThrowOutcome.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param esito esito del tiro o della partita
 * @param puntiApplicati punti effettivamente applicati al tiro
 * @param punteggioResiduo punteggio rimanente dopo il tiro
 * @param turnoAnnullato indicatore di annullamento del turno
 * @param vittoria indicatore di vittoria
 * @param motivo motivazione associata all'esito
 *  
 */
public record ThrowOutcomeDTO(String esito, int puntiApplicati, int punteggioResiduo, boolean turnoAnnullato,
                               boolean vittoria, String motivo) {
}
