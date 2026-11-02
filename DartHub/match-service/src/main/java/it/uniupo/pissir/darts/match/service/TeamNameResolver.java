package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.match.dao.TeamDao;
import it.uniupo.pissir.darts.match.model.Team;

/**
 * Risolve il nome visualizzato di una squadra — quello scelto alla creazione (pagina "Nuova partita", POST /squadre).
 *
 * @see it.uniupo.pissir.darts.match.dao.TeamDao
 *  
 */
public final class TeamNameResolver {

    private final TeamDao teamDao;

    /**
     * Inizializza {@code TeamNameResolver} con i dati e le dipendenze indicati.
     *
     * @param teamDao componente {@link it.uniupo.pissir.darts.match.dao.TeamDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.match.dao.TeamDao
     *  
     */
    public TeamNameResolver(TeamDao teamDao) {
        this.teamDao = teamDao;
    }

    /**
     * Recupera il nome pubblico del partecipante indicato.
     *
     * @param teamId identificativo della squadra
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String resolve(long teamId) {
        return teamDao.findById(teamId).map(Team::nome).orElse("Squadra #" + teamId);
    }

    /**
     * Risolve o assente per la squadra.
     *
     * @param teamId identificativo della squadra
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String resolveOrNull(Long teamId) {
        return teamId == null ? null : resolve(teamId);
    }
}
