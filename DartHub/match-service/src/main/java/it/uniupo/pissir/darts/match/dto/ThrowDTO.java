package it.uniupo.pissir.darts.match.dto;

/**
 * Rappresentazione pubblica di un evento di gioco registrato.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del tiro
 * @param matchId identificativo della partita
 * @param participantId identificativo del partecipante
 * @param tipo tipo della risorsa o dell'evento
 * @param settore settore del bersaglio colpito
 * @param moltiplicatore moltiplicatore del settore
 * @param teamId identificativo della squadra
 * @param punti punti assegnati al tiro
 * @param numeroTurno numero del turno
 * @param esito esito del tiro o della partita
 * @param annullato indicatore di annullamento del tiro
 * @param timestampSensore istante registrato dal sensore
 * @param timestampRicezione istante di ricezione dell'evento
 * @param origine origine dei dati della partita o del tiro
 * @param dartboardId identificativo del bersaglio
 *  
 */
public record ThrowDTO(long id, long matchId, long participantId, String tipo, String settore, String moltiplicatore,
                        Long teamId, int punti, int numeroTurno, String esito, boolean annullato,
                        String timestampSensore, String timestampRicezione, String origine, long dartboardId) {
}
