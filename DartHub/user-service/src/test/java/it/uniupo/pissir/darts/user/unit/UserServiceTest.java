package it.uniupo.pissir.darts.user.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.user.dao.PlayerProfileDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.dto.PlayerLookupDTO;
import it.uniupo.pissir.darts.user.dto.UserDTO;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.PlayerProfile;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.mqtt.UserEventPublisher;
import it.uniupo.pissir.darts.user.service.PasswordHasher;
import it.uniupo.pissir.darts.user.service.UserService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code UserService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private UserDao userDao;
    @Mock
    private PlayerProfileDao playerProfileDao;
    @Mock
    private UserEventPublisher userEventPublisher;

    private UserService userService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        userService = new UserService(connectionProvider, userDao, playerProfileDao, new PasswordHasher(), userEventPublisher);
    }

    /**
     * Verifica {@code register} nel caso giocatore con nickname crea utente approvato e pubblica evento. 
     */
    @Test
    void register_giocatoreConNickname_creaUtenteApprovatoEPubblicaEvento() {
        when(userDao.insert(any(Connection.class), eq("nuovo"), eq("nuovo@test.it"), any(), any(),
                eq(Role.PLAYER), eq(ApprovalStatus.APPROVATO), any(Instant.class))).thenReturn(10L);
        User creato = utente(10L, "nuovo", Role.PLAYER, true, ApprovalStatus.APPROVATO);
        when(userDao.findById(10L)).thenReturn(Optional.of(creato));
        when(playerProfileDao.findById(10L)).thenReturn(Optional.of(new PlayerProfile(10L, "NuovoNick", null)));

        UserDTO dto = userService.register("nuovo", "nuovo@test.it", "password123", "NuovoNick", null);

        assertEquals("nuovo", dto.username());
        assertEquals("PLAYER", dto.role());
        assertEquals("APPROVATO", dto.approvalStatus());
        assertEquals("NuovoNick", dto.nickname());
        verify(playerProfileDao).insert(any(Connection.class), eq(10L), eq("NuovoNick"), eq(null));
        verify(userEventPublisher).publishConteggioCambiato(10L, true);
    }

    /**
     * Verifica {@code register} nel caso gestore locale resta in attesa e non pubblica evento. 
     */
    @Test
    void register_gestoreLocale_restaInAttesaENonPubblicaEvento() {
        when(userDao.insert(any(Connection.class), eq("gestore"), eq("gestore@test.it"), any(), any(),
                eq(Role.LOCALE_ADMIN), eq(ApprovalStatus.IN_ATTESA), any(Instant.class))).thenReturn(11L);
        User creato = utente(11L, "gestore", Role.LOCALE_ADMIN, true, ApprovalStatus.IN_ATTESA);
        when(userDao.findById(11L)).thenReturn(Optional.of(creato));
        when(playerProfileDao.findById(11L)).thenReturn(Optional.empty());

        UserDTO dto = userService.register("gestore", "gestore@test.it", "password123", null, Role.LOCALE_ADMIN);

        assertEquals("IN_ATTESA", dto.approvalStatus());
        verify(playerProfileDao, never()).insert(any(Connection.class), anyLong(), any(), any());
        verify(userEventPublisher, never()).publishConteggioCambiato(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * Verifica {@code register} nel caso ruolo non autoregistrabile validation exception. 
     */
    @Test
    void register_ruoloNonAutoregistrabile_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> userService.register("x", "x@test.it", "password123", null, Role.GAME_ADMIN));

        assertEquals("RUOLO_NON_AUTOREGISTRABILE", e.codice());
        assertEquals(400, e.status());
        verify(userDao, never()).insert(any(Connection.class), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Verifica {@code register} nel caso giocatore senza nickname validation exception. 
     */
    @Test
    void register_giocatoreSenzaNickname_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> userService.register("x", "x@test.it", "password123", "  ", Role.PLAYER));

        assertEquals("CAMPO_MANCANTE", e.codice());
    }

    /**
     * Verifica {@code register} nel caso username vuoto validation exception. 
     */
    @Test
    void register_usernameVuoto_validationException() {
        assertThrows(ValidationException.class,
                () -> userService.register(" ", "x@test.it", "password123", "Nick", Role.PLAYER));
    }

    /**
     * Verifica {@code changePassword} nel caso vecchia password errata unauthorized. 
     */
    @Test
    void changePassword_vecchiaPasswordErrata_unauthorized() {
        PasswordHasher realHasher = new PasswordHasher();
        PasswordHasher.Hashed hashed = realHasher.hash("password-corretta");
        User current = new User(5L, "u", "u@test.it", hashed.hash(), hashed.salt(), Role.PLAYER, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findById(5L)).thenReturn(Optional.of(current));

        assertThrows(UnauthorizedException.class, () -> userService.changePassword(5L, "password-sbagliata", "nuova-password"));
        verify(userDao, never()).update(any(User.class));
    }

    /**
     * Verifica {@code changePassword} nel caso corretta aggiorna hash. 
     */
    @Test
    void changePassword_corretta_aggiornaHash() {
        PasswordHasher realHasher = new PasswordHasher();
        PasswordHasher.Hashed hashed = realHasher.hash("vecchia-password");
        User current = new User(5L, "u", "u@test.it", hashed.hash(), hashed.salt(), Role.PLAYER, true,
                ApprovalStatus.APPROVATO, Instant.now());
        when(userDao.findById(5L)).thenReturn(Optional.of(current));

        userService.changePassword(5L, "vecchia-password", "nuova-password");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).update(captor.capture());
        User aggiornato = captor.getValue();
        assertTrue(realHasher.matches("nuova-password", aggiornato.salt(), aggiornato.passwordHash()));
        assertFalse(realHasher.matches("vecchia-password", aggiornato.salt(), aggiornato.passwordHash()));
    }

    /**
     * Verifica {@code setEnabled} nel caso disabilita utente attivo pubblica evento. 
     */
    @Test
    void setEnabled_disabilitaUtenteAttivo_pubblicaEvento() {
        User current = utente(7L, "u", Role.PLAYER, true, ApprovalStatus.APPROVATO);
        when(userDao.findById(7L)).thenReturn(Optional.of(current));

        userService.setEnabled(7L, false);

        verify(userEventPublisher).publishConteggioCambiato(7L, false);
    }

    /**
     * Verifica {@code setEnabled} nel caso nessun cambio effettivo non pubblica evento. 
     */
    @Test
    void setEnabled_nessunCambioEffettivo_nonPubblicaEvento() {

        User current = utente(7L, "u", Role.PLAYER, false, ApprovalStatus.APPROVATO);
        when(userDao.findById(7L)).thenReturn(Optional.of(current));

        userService.setEnabled(7L, false);

        verify(userEventPublisher, never()).publishConteggioCambiato(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * Verifica {@code changeApprovalStatus} nel caso approva gestore in attesa pubblica evento. 
     */
    @Test
    void changeApprovalStatus_approvaGestoreInAttesa_pubblicaEvento() {
        User current = utente(8L, "gestore", Role.LOCALE_ADMIN, true, ApprovalStatus.IN_ATTESA);
        when(userDao.findById(8L)).thenReturn(Optional.of(current));

        userService.changeApprovalStatus(8L, ApprovalStatus.APPROVATO);

        verify(userEventPublisher).publishConteggioCambiato(8L, true);
    }

    /**
     * Verifica {@code delete} nel caso utente che contava pubblica evento conta falso. 
     */
    @Test
    void delete_utenteCheContava_pubblicaEventoContaFalse() {
        User current = utente(9L, "u", Role.PLAYER, true, ApprovalStatus.APPROVATO);
        when(userDao.findById(9L)).thenReturn(Optional.of(current));

        userService.delete(9L);

        verify(userEventPublisher).publishConteggioCambiato(9L, false);
        verify(userDao).delete(9L);
    }

    /**
     * Verifica {@code delete} nel caso utente gia non contante non pubblica evento. 
     */
    @Test
    void delete_utenteGiaNonContante_nonPubblicaEvento() {
        User current = utente(9L, "u", Role.LOCALE_ADMIN, true, ApprovalStatus.IN_ATTESA);
        when(userDao.findById(9L)).thenReturn(Optional.of(current));

        userService.delete(9L);

        verify(userEventPublisher, never()).publishConteggioCambiato(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * Verifica {@code lookupPlayerByUsername} nel caso non trovato non found. 
     */
    @Test
    void lookupPlayerByUsername_nonTrovato_notFound() {
        when(userDao.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.lookupPlayerByUsername("fantasma"));
    }

    /**
     * Verifica {@code lookupPlayerByUsername} nel caso trovato ma non giocatore non found. 
     */
    @Test
    void lookupPlayerByUsername_trovatoMaNonPlayer_notFound() {
        User adminUtente = utente(1L, "admin", Role.PLATFORM_ADMIN, true, ApprovalStatus.APPROVATO);
        when(userDao.findByUsername("admin")).thenReturn(Optional.of(adminUtente));

        assertThrows(NotFoundException.class, () -> userService.lookupPlayerByUsername("admin"));
    }

    /**
     * Verifica {@code lookupPlayerByUsername} nel caso giocatore disabilitato non found. 
     */
    @Test
    void lookupPlayerByUsername_playerDisabilitato_notFound() {
        User disabilitato = utente(2L, "p", Role.PLAYER, false, ApprovalStatus.APPROVATO);
        when(userDao.findByUsername("p")).thenReturn(Optional.of(disabilitato));

        assertThrows(NotFoundException.class, () -> userService.lookupPlayerByUsername("p"));
    }

    /**
     * Verifica {@code lookupPlayerByUsername} nel caso giocatore valido restituisce dto. 
     */
    @Test
    void lookupPlayerByUsername_playerValido_restituisceDto() {
        User player = utente(3L, "p1", Role.PLAYER, true, ApprovalStatus.APPROVATO);
        when(userDao.findByUsername("p1")).thenReturn(Optional.of(player));
        when(playerProfileDao.findById(3L)).thenReturn(Optional.of(new PlayerProfile(3L, "Nick1", null)));

        PlayerLookupDTO dto = userService.lookupPlayerByUsername("p1");

        assertEquals(3L, dto.id());
        assertEquals("p1", dto.username());
        assertEquals("Nick1", dto.nickname());
    }

    /**
     * Verifica {@code findAll} nel caso delegha alla ricerca sui dao. 
     */
    @Test
    void findAll_deleghaAllaRicercaSuiDao() {
        User player = utente(3L, "p1", Role.PLAYER, true, ApprovalStatus.APPROVATO);
        when(userDao.search(Role.PLAYER, null, Page.of(0, 20))).thenReturn(java.util.List.of(player));
        when(userDao.countSearch(Role.PLAYER, null)).thenReturn(1L);
        when(playerProfileDao.findById(3L)).thenReturn(Optional.empty());

        var result = userService.findAll(Page.of(0, 20), Role.PLAYER, null);

        assertEquals(1, result.contenuto().size());
        assertEquals(1L, result.totaleElementi());
    }

    /**
     * Prepara l'utente con i dati richiesti dal caso di prova.
     *
     * @param id identificativo dell'utente
     * @param username nome dell'account
     * @param role ruolo dell'utente
     * @param enabled indicatore di abilitazione dell'account
     * @param approvalStatus stato di approvazione
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.User} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    private static User utente(long id, String username, Role role, boolean enabled, ApprovalStatus approvalStatus) {
        return new User(id, username, username + "@test.it", "hash", "salt", role, enabled, approvalStatus, Instant.now());
    }
}
