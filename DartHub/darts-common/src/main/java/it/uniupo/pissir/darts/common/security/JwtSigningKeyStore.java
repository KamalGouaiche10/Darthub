package it.uniupo.pissir.darts.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.UUID;

/**
 * Carica o genera la chiave RSA persistente per la firma dei JWT.
 *
 * @see com.nimbusds.jose.jwk.RSAKey
 *  
 */
public final class JwtSigningKeyStore {

    private static final int RSA_KEY_SIZE_BITS = 2048;

    private final RSAKey rsaKey;

    /**
     * Inizializza {@code JwtSigningKeyStore} con i dati e le dipendenze indicati.
     *
     * @param keyFilePath percorso del file contenente la chiave
     * @see java.nio.file.Path
     *  
     */
    public JwtSigningKeyStore(Path keyFilePath) {
        this.rsaKey = loadOrGenerate(keyFilePath);
    }

    /**
     * Inizializza {@code JwtSigningKeyStore} con i dati e le dipendenze indicati.
     *
     * @param rsaKey chiave RSA da utilizzare
     * @see com.nimbusds.jose.jwk.RSAKey
     *  
     */
    private JwtSigningKeyStore(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    /**
     * Genera materiale crittografico temporaneo.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.JwtSigningKeyStore} prodotto o recuperato
     *  
     */
    public static JwtSigningKeyStore ephemeral() {
        try {
            return new JwtSigningKeyStore(new RSAKeyGenerator(RSA_KEY_SIZE_BITS)
                    .keyID(UUID.randomUUID().toString())
                    .generate());
        } catch (JOSEException e) {
            throw new IllegalStateException("Generazione della chiave di firma JWT effimera fallita", e);
        }
    }

    /**
     * Restituisce il materiale crittografico del componente.
     *
     * @return materiale crittografico del componente
     * @see it.uniupo.pissir.darts.common.security.RsaKeyMaterial
     *  
     */
    public RsaKeyMaterial keyMaterial() {
        try {
            return new RsaKeyMaterial(rsaKey.toRSAPublicKey(), rsaKey.toRSAPrivateKey(), rsaKey.getKeyID());
        } catch (JOSEException e) {
            throw new IllegalStateException("Chiave di firma JWT non valida", e);
        }
    }

    /**
     * Espone in JSON soltanto la parte pubblica delle chiavi.
     *
     * @return le sole chiavi pubbliche nel formato JWKS JSON
     *  
     */
    public String publicJwkSetJson() {
        return new JWKSet(rsaKey.toPublicJWK()).toString();
    }

    /**
     * Carica la chiave persistente oppure ne genera una nuova.
     *
     * @param keyFilePath percorso del file contenente la chiave
     * @return l'oggetto {@link com.nimbusds.jose.jwk.RSAKey} prodotto o recuperato
     * @see com.nimbusds.jose.jwk.RSAKey
     *  
     */
    private static RSAKey loadOrGenerate(Path keyFilePath) {
        try {
            if (Files.exists(keyFilePath)) {
                String json = Files.readString(keyFilePath, StandardCharsets.UTF_8);
                return RSAKey.parse(json);
            }
            RSAKey generated = new RSAKeyGenerator(RSA_KEY_SIZE_BITS)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            if (keyFilePath.getParent() != null) {
                Files.createDirectories(keyFilePath.getParent());
            }
            Files.writeString(keyFilePath, generated.toJSONString(), StandardCharsets.UTF_8);
            return generated;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile leggere/scrivere la chiave di firma JWT: " + keyFilePath, e);
        } catch (ParseException e) {
            throw new IllegalStateException("Chiave di firma JWT su disco non valida: " + keyFilePath, e);
        } catch (JOSEException e) {
            throw new IllegalStateException("Generazione della chiave di firma JWT fallita", e);
        }
    }
}
