package it.uniupo.pissir.darts.user.unit;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.security.UserAccessRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica il comportamento di {@code UserAccessRules} con casi di prova JUnit. 
 */
class UserAccessRulesTest {

    /**
     * Verifica {@code requireSelfOrPlatformAdmin} nel caso stesso utente ok. 
     */
    @Test
    void requireSelfOrPlatformAdmin_stessoUtente_ok() {
        AuthenticatedUser player = new AuthenticatedUser(42, Role.PLAYER.name(), null, null);

        assertDoesNotThrow(() -> UserAccessRules.requireSelfOrPlatformAdmin(player, 42));
    }

    /**
     * Verifica {@code requireSelfOrPlatformAdmin} nel caso platform amministratore su altro utente ok. 
     */
    @Test
    void requireSelfOrPlatformAdmin_platformAdminSuAltroUtente_ok() {
        AuthenticatedUser admin = new AuthenticatedUser(1, Role.PLATFORM_ADMIN.name(), null, null);

        assertDoesNotThrow(() -> UserAccessRules.requireSelfOrPlatformAdmin(admin, 42));
    }

    /**
     * Verifica {@code requireSelfOrPlatformAdmin} nel caso altro utente non amministratore forbidden. 
     */
    @Test
    void requireSelfOrPlatformAdmin_altroUtenteNonAdmin_forbidden() {
        AuthenticatedUser player = new AuthenticatedUser(1, Role.PLAYER.name(), null, null);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> UserAccessRules.requireSelfOrPlatformAdmin(player, 42));
        assertEqualsCode("NON_AUTORIZZATO", e);
    }

    /**
     * Verifica {@code requireSelf} nel caso stesso utente ok. 
     */
    @Test
    void requireSelf_stessoUtente_ok() {
        AuthenticatedUser player = new AuthenticatedUser(42, Role.PLAYER.name(), null, null);

        assertDoesNotThrow(() -> UserAccessRules.requireSelf(player, 42));
    }

    /**
     * Verifica {@code requireSelf} nel caso platform amministratore non stesso forbidden. 
     */
    @Test
    void requireSelf_platformAdminNonStesso_forbidden() {

        AuthenticatedUser admin = new AuthenticatedUser(1, Role.PLATFORM_ADMIN.name(), null, null);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> UserAccessRules.requireSelf(admin, 42));
        assertEqualsCode("NON_AUTORIZZATO", e);
    }

    /**
     * Confronta il codice dell'errore con quello atteso dal test.
     *
     * @param expected valore atteso dal test
     * @param e eccezione da gestire
     * @see it.uniupo.pissir.darts.common.exception.ForbiddenException
     *  
     */
    private static void assertEqualsCode(String expected, ForbiddenException e) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, e.codice());
        org.junit.jupiter.api.Assertions.assertEquals(403, e.status());
    }
}
