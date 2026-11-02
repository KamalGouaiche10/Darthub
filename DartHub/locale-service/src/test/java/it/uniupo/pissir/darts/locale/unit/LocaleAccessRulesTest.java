package it.uniupo.pissir.darts.locale.unit;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.locale.model.Role;
import it.uniupo.pissir.darts.locale.security.LocaleAccessRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica il comportamento di {@code LocaleAccessRules} con casi di prova JUnit. 
 */
class LocaleAccessRulesTest {

    /**
     * Verifica {@code requireOwnerOrPlatformAdmin} nel caso proprietario ok. 
     */
    @Test
    void requireOwnerOrPlatformAdmin_proprietario_ok() {
        AuthenticatedUser owner = new AuthenticatedUser(42, Role.LOCALE_ADMIN.name(), null, null);

        assertDoesNotThrow(() -> LocaleAccessRules.requireOwnerOrPlatformAdmin(owner, 42));
    }

    /**
     * Verifica {@code requireOwnerOrPlatformAdmin} nel caso platform amministratore su locale altrui ok. 
     */
    @Test
    void requireOwnerOrPlatformAdmin_platformAdminSuLocaleAltrui_ok() {
        AuthenticatedUser admin = new AuthenticatedUser(1, Role.PLATFORM_ADMIN.name(), null, null);

        assertDoesNotThrow(() -> LocaleAccessRules.requireOwnerOrPlatformAdmin(admin, 42));
    }

    /**
     * Verifica {@code requireOwnerOrPlatformAdmin} nel caso non proprietario non amministratore forbidden. 
     */
    @Test
    void requireOwnerOrPlatformAdmin_nonProprietarioNonAdmin_forbidden() {
        AuthenticatedUser altro = new AuthenticatedUser(1, Role.LOCALE_ADMIN.name(), null, null);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> LocaleAccessRules.requireOwnerOrPlatformAdmin(altro, 42));
        assertEquals("NON_PROPRIETARIO", e.codice());
        assertEquals(403, e.status());
    }

    /**
     * Verifica {@code requireOwnerOrPlatformAdmin} nel caso giocatore non proprietario forbidden. 
     */
    @Test
    void requireOwnerOrPlatformAdmin_playerNonProprietario_forbidden() {
        AuthenticatedUser player = new AuthenticatedUser(42, Role.PLAYER.name(), null, null);

        assertThrows(ForbiddenException.class,
                () -> LocaleAccessRules.requireOwnerOrPlatformAdmin(player, 99));
    }
}
