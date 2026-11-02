package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.match.dao.TeamDao;
import it.uniupo.pissir.darts.match.dao.TeamMemberDao;
import it.uniupo.pissir.darts.match.dto.TeamDTO;
import it.uniupo.pissir.darts.match.model.Team;
import it.uniupo.pissir.darts.match.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code TeamService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.match.dao.TeamDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamDao teamDao;
    @Mock
    private TeamMemberDao teamMemberDao;

    private TeamService teamService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamDao, teamMemberDao);
    }

    /**
     * Verifica {@code create} nel caso nome vuoto validation exception. 
     */
    @Test
    void create_nomeVuoto_validationException() {
        assertThrows(ValidationException.class, () -> teamService.create(" ", 5L));
    }

    /**
     * Verifica {@code create} nel caso valido il creatore diventa il primo membro. 
     */
    @Test
    void create_valido_ilCreatoreDiventaIlPrimoMembro() {
        when(teamDao.insert(any(Team.class))).thenAnswer(inv -> new Team(1L, "I Frecciatori"));
        when(teamMemberDao.findPlayerIdsByTeam(1L)).thenReturn(List.of(5L));

        TeamDTO dto = teamService.create("I Frecciatori", 5L);

        verify(teamMemberDao).insert(1L, 5L);
        assertEquals(List.of(5L), dto.membri());
    }

    /**
     * Verifica {@code requireTeam} nel caso inesistente non found. 
     */
    @Test
    void requireTeam_inesistente_notFound() {
        when(teamDao.findById(99L)).thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> teamService.requireTeam(99L));
        assertEquals("SQUADRA_NON_TROVATA", e.codice());
    }

    /**
     * Verifica {@code update} nel caso nome vuoto mantiene quello attuale. 
     */
    @Test
    void update_nomeVuoto_mantieneQuelloAttuale() {
        when(teamDao.findById(1L)).thenReturn(Optional.of(new Team(1L, "Vecchio nome")));
        when(teamMemberDao.findPlayerIdsByTeam(1L)).thenReturn(List.of());

        teamService.update(1L, " ");

        verify(teamDao).update(new Team(1L, "Vecchio nome"));
    }

    /**
     * Verifica {@code addMember} nel caso squadra inesistente non found. 
     */
    @Test
    void addMember_squadraInesistente_notFound() {
        when(teamDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> teamService.addMember(99L, 5L));
    }

    /**
     * Verifica {@code addMember} nel caso valido delegha al dao. 
     */
    @Test
    void addMember_valido_deleghaAlDao() {
        when(teamDao.findById(1L)).thenReturn(Optional.of(new Team(1L, "Squadra")));

        teamService.addMember(1L, 5L);

        verify(teamMemberDao).insert(1L, 5L);
    }

    /**
     * Verifica {@code removeMember} nel caso delegha al dao. 
     */
    @Test
    void removeMember_deleghaAlDao() {
        when(teamDao.findById(1L)).thenReturn(Optional.of(new Team(1L, "Squadra")));

        teamService.removeMember(1L, 5L);

        verify(teamMemberDao).delete(1L, 5L);
    }

    /**
     * Verifica {@code delete} nel caso squadra inesistente non found. 
     */
    @Test
    void delete_squadraInesistente_notFound() {
        when(teamDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> teamService.delete(99L));
    }
}
