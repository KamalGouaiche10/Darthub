package it.uniupo.pissir.darts.common.security;

import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Genera e verifica i token JWT usati dalla piattaforma.
 *
 * @see it.uniupo.pissir.darts.common.security.JwtConfig
 *  
 */
public final class JwtTokenService {

    private static final String ROLE_EDGE_DEVICE = "EDGE_DEVICE";

    private final JwtConfig config;
    private final JwtGenerator generator;
    private final JwtAuthenticator authenticator;

    /**
     * Inizializza {@code JwtTokenService} con i dati e le dipendenze indicati.
     *
     * @param config configurazione del componente
     * @see it.uniupo.pissir.darts.common.security.JwtConfig
     *  
     */
    public JwtTokenService(JwtConfig config) {
        this.config = config;
        SignatureConfiguration signature = config.signatureConfiguration();
        this.generator = new JwtGenerator(signature);
        this.authenticator = new JwtAuthenticator(signature);
    }

    /**
     * Genera il token di accesso dell'utente.
     *
     * @param userId identificativo dell'utente
     * @param username nome dell'account
     * @param role ruolo dell'utente
     * @return il JWT firmato che identifica l'utente e il suo ruolo
     *  
     */
    public String generateAccessToken(long userId, String username, String role) {
        Instant now = Instant.now();
        JwtClaims claims = new JwtClaims(String.valueOf(userId), username, role, now,
                now.plus(config.accessTokenTtl()), null, null);
        return generate(claims);
    }

    /**
     * Genera il token associato a un dispositivo edge.
     *
     * @param userId identificativo dell'utente
     * @param username nome dell'account
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @return il JWT firmato associato al dispositivo edge
     *  
     */
    public String generateEdgeDeviceToken(long userId, String username, long localeId, long dartboardId) {
        Instant now = Instant.now();
        JwtClaims claims = new JwtClaims(String.valueOf(userId), username, ROLE_EDGE_DEVICE, now,
                now.plus(config.edgeDeviceTokenTtl()), localeId, dartboardId);
        return generate(claims);
    }

    /**
     * Verifica il JWT ed estrae gli attributi autenticati.
     *
     * @param token token da utilizzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.JwtClaims} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.JwtClaims
     *  
     */
    public JwtClaims validateAndExtractClaims(String token) {
        Map<String, Object> claims;
        try {
            claims = authenticator.validateTokenAndGetClaims(token);
        } catch (RuntimeException e) {
            throw new UnauthorizedException("Token JWT non valido o scaduto");
        }
        return toJwtClaims(claims);
    }

    /**
     * Genera te per il JWT token.
     *
     * @param claims attributi del token JWT
     * @return il testo o il valore di configurazione richiesto
     * @see it.uniupo.pissir.darts.common.security.JwtClaims
     *  
     */
    private String generate(JwtClaims claims) {
        Map<String, Object> map = new HashMap<>();
        map.put("sub", claims.sub());
        map.put("username", claims.username());
        map.put("role", claims.role());
        map.put("iat", Date.from(claims.iat()));
        map.put("exp", Date.from(claims.exp()));
        if (claims.localeId() != null) {
            map.put("localeId", claims.localeId());
        }
        if (claims.dartboardId() != null) {
            map.put("dartboardId", claims.dartboardId());
        }
        return generator.generate(map);
    }

    /**
     * Converte gli attributi verificati nell'identità applicativa.
     *
     * @param claims attributi del token JWT
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.JwtClaims} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.JwtClaims
     *  
     */
    private static JwtClaims toJwtClaims(Map<String, Object> claims) {
        return new JwtClaims(
                (String) claims.get("sub"),
                (String) claims.get("username"),
                (String) claims.get("role"),
                toInstant(claims.get("iat")),
                toInstant(claims.get("exp")),
                toLong(claims.get("localeId")),
                toLong(claims.get("dartboardId")));
    }

    /**
     * Converte il valore nel corrispondente istante.
     *
     * @param value dato {@code Object} da elaborare
     * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
     * @see java.time.Instant
     *  
     */
    private static Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }

    /**
     * Converte il valore nel corrispondente intero lungo.
     *
     * @param value dato {@code Object} da elaborare
     * @return il valore numerico calcolato o letto
     *  
     */
    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
