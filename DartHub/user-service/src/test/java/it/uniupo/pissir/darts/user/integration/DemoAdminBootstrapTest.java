package it.uniupo.pissir.darts.user.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.service.DemoAdminBootstrap;
import it.uniupo.pissir.darts.user.service.PasswordHasher;
import it.uniupo.pissir.darts.user.support.TestDatabaseSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code DemoAdminBootstrap} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class DemoAdminBootstrapTest {

    @TempDir
    Path tempDir;

    /**
     * Verifica il caso crea il primo amministratore e si rifiuta di sovrascriverlo. 
     */
    @Test
    void creaIlPrimoAdminESiRifiutaDiSovrascriverlo() {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(tempDir)) {
            long id = DemoAdminBootstrap.create(db, "admin-demo", "admin@example.test", "password-di-demo");
            var admin = new UserDao(db).findById(id).orElseThrow();

            assertEquals(Role.PLATFORM_ADMIN, admin.role());
            assertTrue(new PasswordHasher().matches("password-di-demo", admin.salt(), admin.passwordHash()));
            assertThrows(IllegalStateException.class,
                    () -> DemoAdminBootstrap.create(db, "altro-admin", "altro@example.test", "altra-password"));
            assertEquals(1, new UserDao(db).count());
        }
    }

    /**
     * Verifica il caso rifiuta password troppo corta senza creare utenti. 
     */
    @Test
    void rifiutaPasswordTroppoCortaSenzaCreareUtenti() {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(tempDir)) {
            assertThrows(IllegalArgumentException.class,
                    () -> DemoAdminBootstrap.create(db, "admin-demo", "admin@example.test", "corta"));
            assertEquals(0, new UserDao(db).count());
        }
    }
}
