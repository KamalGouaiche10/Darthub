package it.uniupo.pissir.darts.user.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Genera e revoca le credenziali degli account tecnici dei dispositivi edge.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class DeviceTokenService {

    private final ConnectionProvider connectionProvider;
    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code DeviceTokenService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param userDao componente {@link it.uniupo.pissir.darts.user.dao.UserDao} utilizzato dall'operazione
     * @param passwordHasher password hasher da utilizzare
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public DeviceTokenService(ConnectionProvider connectionProvider, UserDao userDao, PasswordHasher passwordHasher,
                               JwtTokenService jwtTokenService) {
        this.connectionProvider = connectionProvider;
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Emette il token richiesto dopo la verifica delle credenziali.
     *
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param serialNumber numero seriale del dispositivo
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.service.DeviceTokenService.DeviceTokenResult} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.service.DeviceTokenService.DeviceTokenResult
     *  
     */
    public DeviceTokenResult issue(long localeId, long dartboardId, String serialNumber) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String base = serialNumber != null && !serialNumber.isBlank() ? serialNumber : localeId + "-" + dartboardId;
        String username = "edge-" + base + "-" + suffix;

        PasswordHasher.Hashed hashed = passwordHasher.hash(UUID.randomUUID().toString());

        long userId = connectionProvider.withTransaction(connection ->
                userDao.insert(connection, username, username + "@edge.local", hashed.hash(), hashed.salt(),
                        Role.EDGE_DEVICE, ApprovalStatus.APPROVATO, Instant.now()));

        String token = jwtTokenService.generateEdgeDeviceToken(userId, username, localeId, dartboardId);
        return new DeviceTokenResult(userId, token);
    }

    /**
     * Revoca il token o le credenziali indicati.
     *
     * @param deviceUserId identificativo dell'account tecnico del dispositivo
     * @throws NotFoundException se viene rilevato l'errore «L'utente non è un dispositivo edge»
     *  
     */
    public void revoke(long deviceUserId) {
        User user = userDao.findById(deviceUserId)
                .orElseThrow(() -> new NotFoundException("DISPOSITIVO_NON_TROVATO", "Utente dispositivo non trovato"));
        if (user.role() != Role.EDGE_DEVICE) {
            throw new NotFoundException("DISPOSITIVO_NON_TROVATO", "L'utente non è un dispositivo edge");
        }
        userDao.update(new User(user.id(), user.username(), user.email(), user.passwordHash(), user.salt(),
                user.role(), false, user.approvalStatus(), user.createdAt(), user.firstName(), user.lastName()));
    }

    /**
     * Raccoglie i dati di device token risultato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param deviceUserId identificativo dell'account tecnico del dispositivo
     * @param token token da utilizzare
     *  
     */
    public record DeviceTokenResult(long deviceUserId, String token) {
    }
}
