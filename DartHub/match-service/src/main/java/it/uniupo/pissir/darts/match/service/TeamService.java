package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.match.dao.TeamDao;
import it.uniupo.pissir.darts.match.dao.TeamMemberDao;
import it.uniupo.pissir.darts.match.dto.TeamDTO;
import it.uniupo.pissir.darts.match.model.Team;

/**
 * Squadre e membri.
 *
 * @see it.uniupo.pissir.darts.match.dao.TeamDao
 *  
 */
public final class TeamService {

    private final TeamDao teamDao;
    private final TeamMemberDao teamMemberDao;

    /**
     * Inizializza {@code TeamService} con i dati e le dipendenze indicati.
     *
     * @param teamDao componente {@link it.uniupo.pissir.darts.match.dao.TeamDao} utilizzato dall'operazione
     * @param teamMemberDao componente {@link it.uniupo.pissir.darts.match.dao.TeamMemberDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.match.dao.TeamDao
     *  
     */
    public TeamService(TeamDao teamDao, TeamMemberDao teamMemberDao) {
        this.teamDao = teamDao;
        this.teamMemberDao = teamMemberDao;
    }

    /**
     * Richiede che la squadra esista e sia accessibile.
     *
     * @param id identificativo della squadra
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.Team} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Squadra non trovata»
     * @see it.uniupo.pissir.darts.match.model.Team
     *  
     */
    public Team requireTeam(long id) {
        return teamDao.findById(id).orElseThrow(() -> new NotFoundException("SQUADRA_NON_TROVATA", "Squadra non trovata"));
    }

    /**
     * Crea la squadra dopo la validazione dei dati.
     *
     * @param nome nome della risorsa
     * @param creatoDa identificativo dell'utente che ha creato la risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.TeamDTO} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: nome»
     * @see it.uniupo.pissir.darts.match.dto.TeamDTO
     *  
     */
    public TeamDTO create(String nome, long creatoDa) {
        if (nome == null || nome.isBlank()) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: nome");
        }
        Team creata = teamDao.insert(new Team(0, nome));
        teamMemberDao.insert(creata.id(), creatoDa);
        return toDto(creata);
    }

    /**
     * Cerca squadre tutti.
     *
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<TeamDTO> findAll(Page page) {
        var lista = teamDao.findAll(page).stream().map(this::toDto).toList();
        return PagedResult.of(lista, page, teamDao.count());
    }

    /**
     * Cerca la squadra usando i dati della richiesta.
     *
     * @param id identificativo della squadra
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.TeamDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.TeamDTO
     *  
     */
    public TeamDTO findByIdDto(long id) {
        return toDto(requireTeam(id));
    }

    /**
     * Aggiorna la squadra dopo i controlli previsti.
     *
     * @param id identificativo della squadra
     * @param nome nome della risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.TeamDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.TeamDTO
     *  
     */
    public TeamDTO update(long id, String nome) {
        Team current = requireTeam(id);
        String nuovoNome = nome != null && !nome.isBlank() ? nome : current.nome();
        teamDao.update(new Team(current.id(), nuovoNome));
        return findByIdDto(id);
    }

    /**
     * Rimuove la squadra verificando i vincoli dell'operazione.
     *
     * @param id identificativo della squadra
     *  
     */
    public void delete(long id) {
        requireTeam(id);
        teamDao.delete(id);
    }

    /**
     * Aggiunge un giocatore alla squadra.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     *  
     */
    public void addMember(long teamId, long playerId) {
        requireTeam(teamId);
        teamMemberDao.insert(teamId, playerId);
    }

    /**
     * Rimuove un giocatore dalla squadra.
     *
     * @param teamId identificativo della squadra
     * @param playerId identificativo del giocatore
     *  
     */
    public void removeMember(long teamId, long playerId) {
        requireTeam(teamId);
        teamMemberDao.delete(teamId, playerId);
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param team squadra da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.dto.TeamDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.dto.TeamDTO
     *  
     */
    private TeamDTO toDto(Team team) {
        return new TeamDTO(team.id(), team.nome(), teamMemberDao.findPlayerIdsByTeam(team.id()));
    }
}
