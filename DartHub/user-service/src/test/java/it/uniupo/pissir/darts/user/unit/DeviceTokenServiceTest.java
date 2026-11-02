package it.uniupo.pissir.darts.user.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.security.JwtClaims;
import it.uniupo.pissir.darts.common.security.JwtConfig;
import it.uniupo.pissir.darts.common.security.JwtSigningKeyStore;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.service.DeviceTokenService;
import it.uniupo.pissir.darts.user.service.PasswordHasher;
import it.uniupo.pissir.darts.user.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code DeviceTokenService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private UserDao userDao;

    private DeviceTokenService deviceTokenService;
    private JwtTokenService jwtTokenService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        Properties props = new Properties();
        jwtTokenService = new JwtTokenService(new JwtConfig(JwtSigningKeyStore.ephemeral().keyMaterial(), props));
        deviceTokenService = new DeviceTokenService(connectionProvider, userDao, new PasswordHasher(), jwtTokenService);
    }

    /**
     * Verifica {@code issue} nel caso crea utente edge device e restituisce token con claim corretti. 
     */
    @Test
    void issue_creaUtenteEdgeDeviceERestituisceTokenConClaimCorretti() {
        when(userDao.insert(any(Connection.class), any(), any(), any(), any(),
                eq(Role.EDGE_DEVICE), eq(ApprovalStatus.APPROVATO), any(Instant.class))).thenReturn(42L);

        DeviceTokenService.DeviceTokenResult result = deviceTokenService.issue(1L, 2L, "SN-001");

        assertEquals(42L, result.deviceUserId());
        JwtClaims claims = jwtTokenService.validateAndExtractClaims(result.token());
        assertEquals("42", claims.sub());
        assertEquals("EDGE_DEVICE", claims.role());
        assertEquals(1L, claims.localeId());
        assertEquals(2L, claims.dartboardId());
    }

    /**
     * Verifica {@code issue} nel caso username contiene il serial number. 
     */
    @Test
    void issue_usernameContieneIlSerialNumber() {
        when(userDao.insert(any(Connection.class), any(), any(), any(), any(),
                eq(Role.EDGE_DEVICE), eq(ApprovalStatus.APPROVATO), any(Instant.class))).thenReturn(42L);

        deviceTokenService.issue(1L, 2L, "SN-XYZ");

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        verify(userDao).insert(any(Connection.class), usernameCaptor.capture(), any(), any(), any(),
                eq(Role.EDGE_DEVICE), eq(ApprovalStatus.APPROVATO), any(Instant.class));
        assertEquals(true, usernameCaptor.getValue().contains("SN-XYZ"));
    }

    /**
     * Verifica {@code revoke} nel caso dispositivo esistente disabilita utente. 
     */
    @Test
    void revoke_dispositivoEsistente_disabilitaUtente() {
        User device = new User(42L, "edge-1", "edge@test.it", "hash", "salt", Role.EDGE_DEVICE, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findById(42L)).thenReturn(Optional.of(device));

        deviceTokenService.revoke(42L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).update(captor.capture());
        assertFalse(captor.getValue().enabled());
        assertEquals(Role.EDGE_DEVICE, captor.getValue().role());
    }

    /**
     * Verifica {@code revoke} nel caso utente inesistente non found. 
     */
    @Test
    void revoke_utenteInesistente_notFound() {
        when(userDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> deviceTokenService.revoke(99L));
    }

    /**
     * Verifica {@code revoke} nel caso utente non edge device non found. 
     */
    @Test
    void revoke_utenteNonEdgeDevice_notFound() {
        User player = new User(3L, "p1", "p1@test.it", "hash", "salt", Role.PLAYER, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findById(3L)).thenReturn(Optional.of(player));

        assertThrows(NotFoundException.class, () -> deviceTokenService.revoke(3L));
    }
}
