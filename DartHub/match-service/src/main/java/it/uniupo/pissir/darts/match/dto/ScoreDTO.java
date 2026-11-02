package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Risposta di GET /partite/{id/punteggio}: fallback di polling per la web-app quando il live via MQTT/WebSocket non è disponibile.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param matchId identificativo della partita
 * @param stato stato da impostare o verificare
 * @param partecipanteAttivoId identificativo del partecipante a cui spetta il turno
 * @param punteggi punteggi dei partecipanti
 * @see java.util.List
 *  
 */
public record ScoreDTO(long matchId, String stato, Long partecipanteAttivoId, List<ParticipantScoreDTO> punteggi) {

    /**
     * nomeSquadra è il nome scelto alla creazione della squadra (tabella teams di match-service, nessuna chiamata cross-servizio): permette al tabellone di mostrare il nome reale invece di "Squadra #id".
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param playerId identificativo del giocatore
     * @param teamId identificativo della squadra
     * @param nomeSquadra nome della squadra
     * @param nomeGiocatore nome pubblico del giocatore
     * @param ordineTurno posizione del partecipante nell'ordine di gioco
     * @param punteggioCorrente punteggio attuale del partecipante
     * @param ritirato indicatore di ritiro del partecipante
     *  
     */
    public record ParticipantScoreDTO(long participantId, Long playerId, Long teamId, String nomeSquadra,
                                       String nomeGiocatore, int ordineTurno, int punteggioCorrente, boolean ritirato) {
    }
}
