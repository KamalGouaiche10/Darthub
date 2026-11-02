package it.uniupo.pissir.darts.user.unit;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.security.JwtClaims;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtSigningKeyStore;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.user.dao.RefreshTokenDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.dto.TokenResponseDTO;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.RefreshToken;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.service.AuthService;
import it.uniupo.pissir.darts.user.service.PasswordHasher;
import it.uniupo.pissir.darts.user.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code AuthService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private UserDao userDao;
    @Mock
    private RefreshTokenDao refreshTokenDao;

    private PasswordHasher passwordHasher;
    private JwtTokenService jwtTokenService;
    private JwtConfig jwtConfig;
    private AuthService authService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        passwordHasher = new PasswordHasher();
        Properties props = new Properties();
        props.setProperty("jwt.accessTokenTtlMinutes", "30");
        jwtConfig = new JwtConfig(JwtSigningKeyStore.ephemeral().keyMaterial(), props);
        jwtTokenService = new JwtTokenService(jwtConfig);
        authService = new AuthService(connectionProvider, userDao, refreshTokenDao, passwordHasher, jwtTokenService, jwtConfig);
    }

    /**
     * Verifica {@code login} nel caso credenziali corrette restituisce token validi. 
     */
    @Test
    void login_credenzialiCorrette_restituisceTokenValidi() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");
        User user = new User(1L, "admin", "admin@test.it", hashed.hash(), hashed.salt(), Role.PLATFORM_ADMIN, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findByUsername("admin")).thenReturn(Optional.of(user));

        TokenResponseDTO response = authService.login("admin", "admin123");

        assertEquals("Bearer", response.tokenType());
        JwtClaims claims = jwtTokenService.validateAndExtractClaims(response.accessToken());
        assertEquals("1", claims.sub());
        assertEquals("PLATFORM_ADMIN", claims.role());
        verify(refreshTokenDao).insert(any(Connection.class), eq(1L), any(), any(Instant.class));
    }

    /**
     * Verifica {@code login} nel caso password errata unauthorized. 
     */
    @Test
    void login_passwordErrata_unauthorized() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");
        User user = new User(1L, "admin", "admin@test.it", hashed.hash(), hashed.salt(), Role.PLATFORM_ADMIN, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.login("admin", "password-sbagliata"));
        verify(refreshTokenDao, never()).insert(any(Connection.class), anyLong(), any(), any());
    }

    /**
     * Verifica {@code login} nel caso username sconosciuto unauthorized. 
     */
    @Test
    void login_usernameSconosciuto_unauthorized() {
        when(userDao.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login("fantasma", "qualsiasi"));
    }

    /**
     * Verifica {@code login} nel caso utente disabilitato forbidden. 
     */
    @Test
    void login_utenteDisabilitato_forbidden() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");
        User user = new User(1L, "admin", "admin@test.it", hashed.hash(), hashed.salt(), Role.PLATFORM_ADMIN, false,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findByUsername("admin")).thenReturn(Optional.of(user));

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> authService.login("admin", "admin123"));
        assertEquals("UTENTE_DISABILITATO", e.codice());
    }

    /**
     * Verifica {@code refresh} nel caso token valido ruota e restituisce nuovi token. 
     */
    @Test
    void refresh_tokenValido_ruotaERestituisceNuoviToken() {
        User user = new User(2L, "p1", "p1@test.it", "hash", "salt", Role.PLAYER, true, ApprovalStatus.APPROVATO, Instant.now());
        RefreshToken stored = new RefreshToken(99L, 2L, "hash-token", Instant.now().plus(1, ChronoUnit.DAYS), false);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(userDao.findById(2L)).thenReturn(Optional.of(user));

        TokenResponseDTO response = authService.refresh("qualunque-token-in-chiaro");

        verify(refreshTokenDao).revoke(any(Connection.class), eq(99L));
        verify(refreshTokenDao).insert(any(Connection.class), eq(2L), any(), any(Instant.class));
        JwtClaims claims = jwtTokenService.validateAndExtractClaims(response.accessToken());
        assertEquals("2", claims.sub());
    }

    /**
     * Verifica {@code refresh} nel caso token sconosciuto unauthorized. 
     */
    @Test
    void refresh_tokenSconosciuto_unauthorized() {
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refresh("token-inesistente"));
    }

    /**
     * Verifica {@code refresh} nel caso token scaduto unauthorized. 
     */
    @Test
    void refresh_tokenScaduto_unauthorized() {
        RefreshToken scaduto = new RefreshToken(1L, 2L, "hash", Instant.now().minus(1, ChronoUnit.DAYS), false);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(scaduto));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("token-scaduto"));
    }

    /**
     * Verifica {@code refresh} nel caso token gia revocato unauthorized. 
     */
    @Test
    void refresh_tokenGiaRevocato_unauthorized() {
        RefreshToken revocato = new RefreshToken(1L, 2L, "hash", Instant.now().plus(1, ChronoUnit.DAYS), true);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(revocato));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("token-revocato"));
    }

    /**
     * Verifica {@code refresh} nel caso utente disabilitato forbidden. 
     */
    @Test
    void refresh_utenteDisabilitato_forbidden() {
        User user = new User(2L, "p1", "p1@test.it", "hash", "salt", Role.PLAYER, false, ApprovalStatus.APPROVATO, Instant.now());
        RefreshToken stored = new RefreshToken(99L, 2L, "hash-token", Instant.now().plus(1, ChronoUnit.DAYS), false);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(userDao.findById(2L)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> authService.refresh("token"));
    }

    /**
     * Verifica {@code logout} nel caso token valido revoca una sola volta. 
     */
    @Test
    void logout_tokenValido_revocaUnaSolaVolta() {
        RefreshToken stored = new RefreshToken(5L, 2L, "hash-token", Instant.now().plus(1, ChronoUnit.DAYS), false);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(stored));

        authService.logout("qualunque-token-in-chiaro");

        verify(refreshTokenDao).revoke(any(Connection.class), eq(5L));
    }

    /**
     * Verifica {@code logout} nel caso token sconosciuto non lancia enon revoca nulla. 
     */
    @Test
    void logout_tokenSconosciuto_nonLanciaEnonRevocaNulla() {
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("token-inesistente");

        verify(refreshTokenDao, never()).revoke(any(Connection.class), anyLong());
    }

    /**
     * Verifica {@code logout} nel caso token gia revocato non revoca di nuovo. 
     */
    @Test
    void logout_tokenGiaRevocato_nonRevocaDiNuovo() {
        RefreshToken giaRevocato = new RefreshToken(5L, 2L, "hash-token", Instant.now().plus(1, ChronoUnit.DAYS), true);
        when(refreshTokenDao.findByTokenHash(any())).thenReturn(Optional.of(giaRevocato));

        authService.logout("token");

        verify(refreshTokenDao, never()).revoke(any(Connection.class), anyLong());
    }

    /**
     * Verifica {@code issueTokens} nel caso due login consecutivi aggiornamento token diversi. 
     */
    @Test
    void issueTokens_dueLoginConsecutivi_refreshTokenDiversi() {
        PasswordHasher.Hashed hashed = passwordHasher.hash("admin123");
        User user = new User(1L, "admin", "admin@test.it", hashed.hash(), hashed.salt(), Role.PLATFORM_ADMIN, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findByUsername("admin")).thenReturn(Optional.of(user));

        TokenResponseDTO primo = authService.login("admin", "admin123");
        TokenResponseDTO secondo = authService.login("admin", "admin123");

        assertNotEquals(primo.refreshToken(), secondo.refreshToken());
    }
}
