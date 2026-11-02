package it.uniupo.pissir.darts.user.unit;

import it.uniupo.pissir.darts.user.service.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code PasswordHasher} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.user.service.PasswordHasher
 *  
 */
class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    /**
     * Verifica {@code hashThenMatches} nel caso stessa password vero. 
     */
    @Test
    void hashThenMatches_stessaPassword_true() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");

        assertTrue(passwordHasher.matches("admin123", hashed.salt(), hashed.hash()));
    }

    /**
     * Verifica {@code matches} nel caso wrong password falso. 
     */
    @Test
    void matches_wrongPassword_false() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");

        assertFalse(passwordHasher.matches("password-sbagliata", hashed.salt(), hashed.hash()));
    }

    /**
     * Verifica {@code hash} nel caso stessa password due volte salt e hash diversi. 
     */
    @Test
    void hash_stessaPasswordDueVolte_saltEHashDiversi() {
        PasswordHasher.Hashed prima = passwordHasher.hash("stessaPassword");
        PasswordHasher.Hashed seconda = passwordHasher.hash("stessaPassword");

        assertNotEquals(prima.salt(), seconda.salt());
        assertNotEquals(prima.hash(), seconda.hash());

        assertTrue(passwordHasher.matches("stessaPassword", prima.salt(), prima.hash()));
        assertTrue(passwordHasher.matches("stessaPassword", seconda.salt(), seconda.hash()));
    }

    /**
     * Verifica {@code matches} nel caso hash alterato a stesso salt falso. 
     */
    @Test
    void matches_hashAlteratoAStessoSalt_false() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");
        PasswordHasher.Hashed altroHash = passwordHasher.hash("altra-password");

        assertFalse(passwordHasher.matches("admin123", hashed.salt(), altroHash.hash()));
    }

    /**
     * Verifica {@code matches} nel caso vuoto password non corrisponde a hash non vuoto. 
     */
    @Test
    void matches_emptyPassword_nonCorrispondeAHashNonVuoto() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");

        assertFalse(passwordHasher.matches("", hashed.salt(), hashed.hash()));
    }
}
