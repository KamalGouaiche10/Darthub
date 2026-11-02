package it.uniupo.pissir.darts.game.engine;

import java.util.Map;

/**
 * Stato di una partita, ricostruito dallo storico eventi prima di valutarne uno nuovo: stesso pattern di ricostruzione già usato da TurnManager lato match-service e da MatchSessionManager lato edge, reso generico.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param punteggiPerPartecipante punteggi indicizzati per identificativo del partecipante
 * @param turnoCorrente numero del turno attivo
 * @param partecipanteAttivo partecipante a cui spetta il turno
 * @param extra dati aggiuntivi dell'evento
 * @see java.util.Map
 *  
 */
public record MatchState(Map<Long, Integer> punteggiPerPartecipante, int turnoCorrente,
                          Long partecipanteAttivo, Map<String, Object> extra) {

    /**
     * Legge il punteggio associato al partecipante indicato.
     *
     * @param participantId identificativo del partecipante
     * @return il valore numerico calcolato o letto
     *  
     */
    public int punteggioDi(long participantId) {
        Integer punteggio = punteggiPerPartecipante.get(participantId);
        return punteggio != null ? punteggio : 0;
    }
}
