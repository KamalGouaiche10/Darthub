package it.uniupo.pissir.darts.common.security;

import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica il comportamento di {@code JwtTokenService} con casi di prova JUnit. 
 */
class JwtTokenServiceTest {

    /**
     * Verifica il caso token scaduto con firma valida viene rifiutato. 
     */
    @Test
    void tokenScadutoConFirmaValidaVieneRifiutato() {
        RsaKeyMaterial keys = JwtSigningKeyStore.ephemeral().keyMaterial();
        JwtTokenService issuer = new JwtTokenService(config(keys, "-1"));
        JwtTokenService verifier = new JwtTokenService(config(keys, "30"));

        String token = issuer.generateAccessToken(42, "giocatore", "PLAYER");

        assertThrows(UnauthorizedException.class, () -> verifier.validateAndExtractClaims(token));
    }

    /**
     * Verifica il caso token valido con stessa chiave viene accettato. 
     */
    @Test
    void tokenValidoConStessaChiaveVieneAccettato() {
        RsaKeyMaterial keys = JwtSigningKeyStore.ephemeral().keyMaterial();
        JwtTokenService issuer = new JwtTokenService(config(keys, "30"));
        JwtTokenService verifier = new JwtTokenService(config(keys, "30"));

        JwtClaims claims = verifier.validateAndExtractClaims(issuer.generateAccessToken(42, "giocatore", "PLAYER"));

        assertEquals("42", claims.sub());
        assertEquals("PLAYER", claims.role());
    }

    /**
     * Prepara la configurazione JWT usata dal test.
     *
     * @param keys chiavi crittografiche disponibili
     * @param minutes intervallo in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.JwtConfig} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.JwtConfig
     *  
     */
    private static JwtConfig config(RsaKeyMaterial keys, String minutes) {
        Properties properties = new Properties();
        properties.setProperty("jwt.accessTokenTtlMinutes", minutes);
        return new JwtConfig(keys, properties);
    }
}
