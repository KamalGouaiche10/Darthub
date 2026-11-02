package it.uniupo.pissir.darts.user.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code UserDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class UserDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private UserDao userDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        userDao = new UserDao(connectionProvider);
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
        User inserito = userDao.insert(nuovoUtente("mario", "mario@test.it", Role.PLAYER));

        Optional<User> trovato = userDao.findById(inserito.id());

        assertTrue(trovato.isPresent());
        assertEquals("mario", trovato.get().username());
        assertEquals(Role.PLAYER, trovato.get().role());
        assertEquals(ApprovalStatus.APPROVATO, trovato.get().approvalStatus());
        assertTrue(trovato.get().enabled());
    }

    /**
     * Verifica {@code findById} nel caso inesistente vuoto. 
     */
    @Test
    void findById_inesistente_vuoto() {
        assertTrue(userDao.findById(999L).isEmpty());
    }

    /**
     * Verifica {@code findByUsername} nel caso trova utente inserito in transazione. 
     */
    @Test
    void findByUsername_trovaUtenteInseritoInTransazione() {
        long id = connectionProvider.withTransaction(connection ->
                userDao.insert(connection, "luigi", "luigi@test.it", "hash", "salt", Role.PLAYER,
                        ApprovalStatus.APPROVATO, Instant.now()));

        Optional<User> trovato = userDao.findByUsername("luigi");

        assertTrue(trovato.isPresent());
        assertEquals(id, trovato.get().id());
    }

    /**
     * Verifica {@code insert} nel caso username duplicato conflict exception. 
     */
    @Test
    void insert_usernameDuplicato_conflictException() {
        userDao.insert(nuovoUtente("duplicato", "a@test.it", Role.PLAYER));

        ConflictException e = assertThrows(ConflictException.class,
                () -> userDao.insert(nuovoUtente("duplicato", "b@test.it", Role.PLAYER)));
        assertEquals("USERNAME_DUPLICATO", e.codice());
    }

    /**
     * Verifica {@code insert} nel caso email duplicata conflict exception. 
     */
    @Test
    void insert_emailDuplicata_conflictException() {
        userDao.insert(nuovoUtente("uno", "stessa@test.it", Role.PLAYER));

        ConflictException e = assertThrows(ConflictException.class,
                () -> userDao.insert(nuovoUtente("due", "stessa@test.it", Role.PLAYER)));
        assertEquals("EMAIL_DUPLICATA", e.codice());
    }

    /**
     * Verifica {@code search} nel caso filtra per ruolo e approval stato. 
     */
    @Test
    void search_filtraPerRuoloEApprovalStatus() {
        userDao.insert(nuovoUtente("player1", "player1@test.it", Role.PLAYER));
        userDao.insert(nuovoUtente("player2", "player2@test.it", Role.PLAYER));
        User gestore = userDao.insert(new User(0, "gestore", "gestore@test.it", "hash", "salt", Role.LOCALE_ADMIN,
                true, ApprovalStatus.IN_ATTESA, Instant.now()));

        List<User> giocatori = userDao.search(Role.PLAYER, null, Page.of(0, 20));
        List<User> inAttesa = userDao.search(null, ApprovalStatus.IN_ATTESA, Page.of(0, 20));

        assertEquals(2, giocatori.size());
        assertEquals(1, inAttesa.size());
        assertEquals(gestore.id(), inAttesa.get(0).id());
    }

    /**
     * Verifica {@code search} nel caso paginazione. 
     */
    @Test
    void search_paginazione() {
        for (int i = 0; i < 5; i++) {
            userDao.insert(nuovoUtente("player" + i, "player" + i + "@test.it", Role.PLAYER));
        }

        List<User> primaPagina = userDao.search(null, null, Page.of(0, 2));
        List<User> secondaPagina = userDao.search(null, null, Page.of(1, 2));

        assertEquals(2, primaPagina.size());
        assertEquals(2, secondaPagina.size());
        assertTrue(primaPagina.get(0).id() != secondaPagina.get(0).id());
    }

    /**
     * Verifica {@code countSearch} nel caso conta solo i filtrati. 
     */
    @Test
    void countSearch_contaSoloIFiltrati() {
        userDao.insert(nuovoUtente("player1", "player1@test.it", Role.PLAYER));
        userDao.insert(new User(0, "gestore", "gestore@test.it", "hash", "salt", Role.LOCALE_ADMIN,
                true, ApprovalStatus.IN_ATTESA, Instant.now()));

        assertEquals(1, userDao.countSearch(Role.PLAYER, null));
        assertEquals(2, userDao.countSearch(null, null));
    }

    /**
     * Verifica {@code update} nel caso modifica email e ruolo. 
     */
    @Test
    void update_modificaEmailERuolo() {
        User inserito = userDao.insert(nuovoUtente("mario", "mario@test.it", Role.PLAYER));
        User modificato = new User(inserito.id(), inserito.username(), "nuova@test.it", inserito.passwordHash(),
                inserito.salt(), Role.GAME_ADMIN, false, ApprovalStatus.APPROVATO, inserito.createdAt());

        userDao.update(modificato);

        User rileto = userDao.findById(inserito.id()).orElseThrow();
        assertEquals("nuova@test.it", rileto.email());
        assertEquals(Role.GAME_ADMIN, rileto.role());
        assertEquals(false, rileto.enabled());
    }

    /**
     * Verifica {@code delete} nel caso rimuove utente. 
     */
    @Test
    void delete_rimuoveUtente() {
        User inserito = userDao.insert(nuovoUtente("mario", "mario@test.it", Role.PLAYER));

        userDao.delete(inserito.id());

        assertTrue(userDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code count} nel caso conta tutti gli utenti. 
     */
    @Test
    void count_contaTuttiGliUtenti() {
        userDao.insert(nuovoUtente("a", "a@test.it", Role.PLAYER));
        userDao.insert(nuovoUtente("b", "b@test.it", Role.PLAYER));

        assertEquals(2, userDao.count());
    }

    /**
     * Prepara l'utente con i dati richiesti dal caso di prova.
     *
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param role ruolo dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.User} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    private static User nuovoUtente(String username, String email, Role role) {
        return new User(0, username, email, "hash", "salt", role, true, ApprovalStatus.APPROVATO, Instant.now());
    }
}
