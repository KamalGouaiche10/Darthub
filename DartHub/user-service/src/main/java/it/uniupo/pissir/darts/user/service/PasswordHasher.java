package it.uniupo.pissir.darts.user.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Calcola e verifica gli hash delle password con un sale dedicato.
 *
 * @see java.security.SecureRandom
 *  
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    /**
     * Calcola l'hash della password usando il sale indicato.
     *
     * @param password password da verificare o registrare
     * @return l'hash della password codificato in Base64
     * @see it.uniupo.pissir.darts.user.service.PasswordHasher.Hashed
     *  
     */
    public Hashed hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        byte[] derived = pbkdf2(password, salt);
        return new Hashed(Base64.getEncoder().encodeToString(derived), Base64.getEncoder().encodeToString(salt));
    }

    /**
     * Verifica che la password corrisponda all'hash memorizzato.
     *
     * @param password password da verificare o registrare
     * @param saltBase64 sale codificato in Base64
     * @param expectedHashBase64 hash atteso codificato in Base64
     * @return l'esito della condizione verificata
     *  
     */
    public boolean matches(String password, String saltBase64, String expectedHashBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] expected = Base64.getDecoder().decode(expectedHashBase64);
        byte[] actual = pbkdf2(password, salt);
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * Deriva la chiave della password con PBKDF2.
     *
     * @param password password da verificare o registrare
     * @param salt sale usato nel calcolo dell'hash
     * @return i dati binari prodotti dall'operazione
     *  
     */
    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 non disponibile", e);
        }
    }

    /**
     * Raccoglie i dati di hashed.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param hash hash della password
     * @param salt sale usato nel calcolo dell'hash
     *  
     */
    public record Hashed(String hash, String salt) {
    }
}
