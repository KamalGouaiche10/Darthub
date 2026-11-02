package it.uniupo.pissir.darts.user.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.user.dao.RefreshTokenDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.RefreshToken;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code RefreshTokenDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class RefreshTokenDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private UserDao userDao;
    private RefreshTokenDao refreshTokenDao;
    private long userId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        userDao = new UserDao(connectionProvider);
        refreshTokenDao = new RefreshTokenDao(connectionProvider);
        User inserito = userDao.insert(new User(0, "mario", "mario@test.it", "hash", "salt", Role.PLAYER,
                true, ApprovalStatus.APPROVATO, Instant.now()));
        userId = inserito.id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindByTokenHash} nel caso round trip. 
     */
    @Test
    void insertEFindByTokenHash_roundTrip() {
        Instant scadenza = Instant.now().plus(30, ChronoUnit.DAYS);

        RefreshToken inserito = refreshTokenDao.insert(new RefreshToken(0, userId, "hash-abc", scadenza, false));

        Optional<RefreshToken> trovato = refreshTokenDao.findByTokenHash("hash-abc");
        assertTrue(trovato.isPresent());
        assertEquals(inserito.id(), trovato.get().id());
        assertFalse(trovato.get().revoked());
    }

    /**
     * Verifica {@code findByTokenHash} nel caso inesistente vuoto. 
     */
    @Test
    void findByTokenHash_inesistente_vuoto() {
        assertTrue(refreshTokenDao.findByTokenHash("non-esiste").isEmpty());
    }

    /**
     * Verifica {@code revoke} nel caso imposta flag revoked. 
     */
    @Test
    void revoke_impostaFlagRevoked() {
        RefreshToken inserito = refreshTokenDao.insert(
                new RefreshToken(0, userId, "hash-def", Instant.now().plus(1, ChronoUnit.DAYS), false));

        connectionProvider.withTransaction(connection -> {
            refreshTokenDao.revoke(connection, inserito.id());
            return null;
        });

        RefreshToken rileto = refreshTokenDao.findByTokenHash("hash-def").orElseThrow();
        assertTrue(rileto.revoked());
    }

    /**
     * Verifica {@code delete} nel caso rimuove token. 
     */
    @Test
    void delete_rimuoveToken() {
        RefreshToken inserito = refreshTokenDao.insert(
                new RefreshToken(0, userId, "hash-ghi", Instant.now().plus(1, ChronoUnit.DAYS), false));

        refreshTokenDao.delete(inserito.id());

        assertTrue(refreshTokenDao.findByTokenHash("hash-ghi").isEmpty());
    }

    /**
     * Verifica {@code delete} nel caso utente cascada sui aggiornamento token. 
     */
    @Test
    void delete_utente_cascadaSuiRefreshToken() {
        refreshTokenDao.insert(new RefreshToken(0, userId, "hash-jkl", Instant.now().plus(1, ChronoUnit.DAYS), false));

        userDao.delete(userId);

        assertTrue(refreshTokenDao.findByTokenHash("hash-jkl").isEmpty());
    }

    /**
     * Verifica {@code count} nel caso conta i token inseriti. 
     */
    @Test
    void count_contaITokenInseriti() {
        refreshTokenDao.insert(new RefreshToken(0, userId, "hash-1", Instant.now().plus(1, ChronoUnit.DAYS), false));
        refreshTokenDao.insert(new RefreshToken(0, userId, "hash-2", Instant.now().plus(1, ChronoUnit.DAYS), false));

        assertEquals(2, refreshTokenDao.count());
    }
}
