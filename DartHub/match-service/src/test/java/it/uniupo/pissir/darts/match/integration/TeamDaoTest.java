package it.uniupo.pissir.darts.match.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.match.dao.TeamDao;
import it.uniupo.pissir.darts.match.dao.TeamMemberDao;
import it.uniupo.pissir.darts.match.model.Team;
import it.uniupo.pissir.darts.match.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code TeamDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class TeamDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private TeamDao teamDao;
    private TeamMemberDao teamMemberDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        teamDao = new TeamDao(connectionProvider);
        teamMemberDao = new TeamMemberDao(connectionProvider);
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip. 
     */
    @Test
    void insertEFindById_roundTrip() {
        Team inserita = teamDao.insert(new Team(0, "I Frecciatori"));

        assertEquals("I Frecciatori", teamDao.findById(inserita.id()).orElseThrow().nome());
    }

    /**
     * Verifica {@code insert} nel caso nome duplicato conflict exception. 
     */
    @Test
    void insert_nomeDuplicato_conflictException() {
        teamDao.insert(new Team(0, "Duplicata"));

        ConflictException e = assertThrows(ConflictException.class, () -> teamDao.insert(new Team(0, "Duplicata")));
        assertEquals("SQUADRA_DUPLICATA", e.codice());
    }

    /**
     * Verifica {@code update} nel caso modifica nome. 
     */
    @Test
    void update_modificaNome() {
        Team inserita = teamDao.insert(new Team(0, "Vecchio"));

        teamDao.update(new Team(inserita.id(), "Nuovo"));

        assertEquals("Nuovo", teamDao.findById(inserita.id()).orElseThrow().nome());
    }

    /**
     * Verifica {@code delete} nel caso rimuove squadra. 
     */
    @Test
    void delete_rimuoveSquadra() {
        Team inserita = teamDao.insert(new Team(0, "Da eliminare"));

        teamDao.delete(inserita.id());

        assertTrue(teamDao.findById(inserita.id()).isEmpty());
    }

    /**
     * Verifica {@code delete} nel caso squadra cascada sui membri. 
     */
    @Test
    void delete_squadra_cascadaSuiMembri() {
        Team inserita = teamDao.insert(new Team(0, "Squadra"));
        teamMemberDao.insert(inserita.id(), 5L);

        teamDao.delete(inserita.id());

        assertTrue(teamMemberDao.findPlayerIdsByTeam(inserita.id()).isEmpty());
    }

    /**
     * Verifica {@code teamMember} nel caso insert e find e membro duplicato. 
     */
    @Test
    void teamMember_insertEFind_eMembroDuplicato() {
        Team squadra = teamDao.insert(new Team(0, "Squadra"));

        teamMemberDao.insert(squadra.id(), 5L);
        teamMemberDao.insert(squadra.id(), 6L);

        List<Long> membri = teamMemberDao.findPlayerIdsByTeam(squadra.id());
        assertEquals(List.of(5L, 6L), membri);
        assertTrue(teamMemberDao.isMember(squadra.id(), 5L));

        ConflictException e = assertThrows(ConflictException.class, () -> teamMemberDao.insert(squadra.id(), 5L));
        assertEquals("MEMBRO_GIA_PRESENTE", e.codice());
    }

    /**
     * Verifica {@code teamMember} nel caso delete rimuove il membro. 
     */
    @Test
    void teamMember_delete_rimuoveIlMembro() {
        Team squadra = teamDao.insert(new Team(0, "Squadra"));
        teamMemberDao.insert(squadra.id(), 5L);

        teamMemberDao.delete(squadra.id(), 5L);

        assertTrue(teamMemberDao.findPlayerIdsByTeam(squadra.id()).isEmpty());
    }
}
