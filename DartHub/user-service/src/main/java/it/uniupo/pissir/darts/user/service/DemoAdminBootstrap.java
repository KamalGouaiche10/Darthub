package it.uniupo.pissir.darts.user.service;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.Role;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Prepara l'amministratore della demo quando la configurazione lo richiede. 
 */
public final class DemoAdminBootstrap {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private DemoAdminBootstrap() {
    }

    /**
     * Crea il demo amministratore bootstrap dopo la validazione dei dati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param password password da verificare o registrare
     * @return il valore numerico calcolato o letto
     * @throws IllegalArgumentException se viene rilevato l'errore «Username, email e password di almeno 8 caratteri sono obbligatori»
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public static long create(ConnectionProvider connectionProvider, String username, String email, String password) {
        if (username == null || username.isBlank() || email == null || email.isBlank()
                || password == null || password.length() < 8) {
            throw new IllegalArgumentException("Username, email e password di almeno 8 caratteri sono obbligatori");
        }
        UserDao userDao = new UserDao(connectionProvider);
        PasswordHasher.Hashed hashed = new PasswordHasher().hash(password);
        return connectionProvider.withTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE role = 'PLATFORM_ADMIN'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) != 0) {
                    throw new IllegalStateException("Esiste già un PLATFORM_ADMIN: bootstrap non eseguito");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Controllo amministratore fallito", e);
            }
            return userDao.insert(connection, username.trim(), email.trim(), hashed.hash(), hashed.salt(),
                    Role.PLATFORM_ADMIN, ApprovalStatus.APPROVATO, Instant.now());
        });
    }
}
