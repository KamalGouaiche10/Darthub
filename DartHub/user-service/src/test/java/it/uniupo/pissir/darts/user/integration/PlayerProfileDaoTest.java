package it.uniupo.pissir.darts.user.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.user.dao.PlayerProfileDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.PlayerProfile;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code PlayerProfileDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class PlayerProfileDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private UserDao userDao;
    private PlayerProfileDao playerProfileDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        userDao = new UserDao(connectionProvider);
        playerProfileDao = new PlayerProfileDao(connectionProvider);
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
        long userId = creaUtente("mario");

        playerProfileDao.insert(new PlayerProfile(userId, "SuperMario", "http://avatar.png"));

        Optional<PlayerProfile> trovato = playerProfileDao.findById(userId);
        assertTrue(trovato.isPresent());
        assertEquals("SuperMario", trovato.get().nickname());
        assertEquals("http://avatar.png", trovato.get().avatarUrl());
    }

    /**
     * Verifica {@code update} nel caso modifica nickname e avatar. 
     */
    @Test
    void update_modificaNicknameEAvatar() {
        long userId = creaUtente("mario");
        playerProfileDao.insert(new PlayerProfile(userId, "VecchioNick", null));

        playerProfileDao.update(new PlayerProfile(userId, "NuovoNick", "http://nuovo.png"));

        PlayerProfile rileto = playerProfileDao.findById(userId).orElseThrow();
        assertEquals("NuovoNick", rileto.nickname());
        assertEquals("http://nuovo.png", rileto.avatarUrl());
    }

    /**
     * Verifica {@code delete} nel caso rimuove profilo. 
     */
    @Test
    void delete_rimuoveProfilo() {
        long userId = creaUtente("mario");
        playerProfileDao.insert(new PlayerProfile(userId, "Nick", null));

        playerProfileDao.delete(userId);

        assertTrue(playerProfileDao.findById(userId).isEmpty());
    }

    /**
     * Verifica {@code delete} nel caso utente cascada sul profilo. 
     */
    @Test
    void delete_utente_cascadaSulProfilo() {
        long userId = creaUtente("mario");
        playerProfileDao.insert(new PlayerProfile(userId, "Nick", null));

        userDao.delete(userId);

        assertTrue(playerProfileDao.findById(userId).isEmpty());
    }

    /**
     * Prepara i dati di long per il caso di prova.
     *
     * @param username nome dell'account
     * @return il valore numerico calcolato o letto
     *  
     */
    private long creaUtente(String username) {
        User inserito = userDao.insert(new User(0, username, username + "@test.it", "hash", "salt", Role.PLAYER,
                true, ApprovalStatus.APPROVATO, Instant.now()));
        return inserito.id();
    }
}
