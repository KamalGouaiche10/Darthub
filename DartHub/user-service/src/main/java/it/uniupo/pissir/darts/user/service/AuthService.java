package it.uniupo.pissir.darts.user.service;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.user.dao.RefreshTokenDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.dto.TokenResponseDTO;
import it.uniupo.pissir.darts.user.model.RefreshToken;
import it.uniupo.pissir.darts.user.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Gestisce autenticazione, rinnovo e revoca delle sessioni.
 *
 * @see java.time.Duration
 *  
 */
public final class AuthService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final ConnectionProvider connectionProvider;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;
    private final SecureRandom random = new SecureRandom();

    /**
     * Inizializza {@code AuthService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param userDao componente {@link it.uniupo.pissir.darts.user.dao.UserDao} utilizzato dall'operazione
     * @param refreshTokenDao componente {@link it.uniupo.pissir.darts.user.dao.RefreshTokenDao} utilizzato dall'operazione
     * @param passwordHasher password hasher da utilizzare
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @param jwtConfig configurazione dei token JWT
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public AuthService(ConnectionProvider connectionProvider, UserDao userDao, RefreshTokenDao refreshTokenDao,
                        PasswordHasher passwordHasher, JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
        this.connectionProvider = connectionProvider;
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.passwordHasher = passwordHasher;
        this.jwtTokenService = jwtTokenService;
        this.jwtConfig = jwtConfig;
    }

    /**
     * Verifica le credenziali ed emette i token della sessione.
     *
     * @param username nome dell'account
     * @param password password da verificare o registrare
     * @return i token di accesso e aggiornamento della sessione autenticata
     * @throws UnauthorizedException se viene rilevato l'errore «Credenziali non valide»
     * @throws ForbiddenException se viene rilevato l'errore «L'utente è disabilitato»
     * @see it.uniupo.pissir.darts.user.dto.TokenResponseDTO
     *  
     */
    public TokenResponseDTO login(String username, String password) {
        String identifier = username == null ? "" : username.trim();
        User user = (identifier.contains("@") ? userDao.findByEmail(identifier) : userDao.findByUsername(identifier))
                .orElseThrow(() -> new UnauthorizedException("Credenziali non valide"));
        if (!passwordHasher.matches(password == null ? "" : password, user.salt(), user.passwordHash())) {
            throw new UnauthorizedException("Credenziali non valide");
        }
        if (!user.enabled()) {
            throw new ForbiddenException("UTENTE_DISABILITATO", "L'utente è disabilitato");
        }
        return issueTokens(user);
    }

    /**
     * Verifica il token di aggiornamento e rinnova la sessione.
     *
     * @param refreshToken token di aggiornamento della sessione
     * @return i token della sessione rinnovata
     * @throws ForbiddenException se viene rilevato l'errore «L'utente è disabilitato»
     * @throws UnauthorizedException se viene rilevato l'errore «Utente non trovato»
     * @see it.uniupo.pissir.darts.user.dto.TokenResponseDTO
     *  
     */
    public TokenResponseDTO refresh(String refreshToken) {
        RefreshToken stored = requireValidRefreshToken(refreshToken);
        User user = userDao.findById(stored.userId())
                .orElseThrow(() -> new UnauthorizedException("Utente non trovato"));
        if (!user.enabled()) {
            throw new ForbiddenException("UTENTE_DISABILITATO", "L'utente è disabilitato");
        }
        connectionProvider.withTransaction(connection -> {
            refreshTokenDao.revoke(connection, stored.id());
            return null;
        });
        return issueTokens(user);
    }

    /**
     * Revoca il token di aggiornamento della sessione.
     *
     * @param refreshToken token di aggiornamento della sessione
     *  
     */
    public void logout(String refreshToken) {
        String tokenHash = hashToken(refreshToken == null ? "" : refreshToken);
        connectionProvider.withTransaction(connection -> {
            refreshTokenDao.findByTokenHash(tokenHash)
                    .filter(t -> !t.revoked())
                    .ifPresent(t -> refreshTokenDao.revoke(connection, t.id()));
            return null;
        });
    }

    /**
     * Recupera e valida il token di aggiornamento.
     *
     * @param refreshToken token di aggiornamento della sessione
     * @return il token persistito ancora valido e non revocato
     * @throws UnauthorizedException se viene rilevato l'errore «Refresh token non valido o scaduto»
     * @see it.uniupo.pissir.darts.user.model.RefreshToken
     *  
     */
    private RefreshToken requireValidRefreshToken(String refreshToken) {
        String tokenHash = hashToken(refreshToken == null ? "" : refreshToken);
        Instant now = Instant.now();
        return refreshTokenDao.findByTokenHash(tokenHash)
                .filter(t -> !t.revoked() && t.expiresAt().isAfter(now))
                .orElseThrow(() -> new UnauthorizedException("Refresh token non valido o scaduto"));
    }

    /**
     * Genera i token di accesso e aggiornamento e registra la sessione.
     *
     * @param user utente da elaborare
     * @return i token emessi e la durata del token di accesso
     * @see it.uniupo.pissir.darts.user.dto.TokenResponseDTO
     *  
     */
    private TokenResponseDTO issueTokens(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user.id(), user.username(), user.role().name());
        String refreshToken = generateRefreshTokenValue();
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL);
        connectionProvider.withTransaction(connection -> {
            refreshTokenDao.insert(connection, user.id(), hashToken(refreshToken), expiresAt);
            return null;
        });
        return new TokenResponseDTO(accessToken, refreshToken, "Bearer", jwtConfig.accessTokenTtl().toSeconds());
    }

    /**
     * Genera un valore casuale per il token di aggiornamento.
     *
     * @return il valore casuale del token di aggiornamento
     *  
     */
    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Calcola l'hash del token da conservare.
     *
     * @param token token da utilizzare
     * @return l'hash del token
     *  
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
