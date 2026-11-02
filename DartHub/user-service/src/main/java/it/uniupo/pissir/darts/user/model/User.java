package it.uniupo.pissir.darts.user.model;

import java.time.Instant;

/**
 * Riga della tabella users.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo dell'utente
 * @param username nome dell'account
 * @param email indirizzo email dell'account
 * @param passwordHash hash della password
 * @param salt sale usato nel calcolo dell'hash
 * @param role ruolo dell'utente
 * @param enabled indicatore di abilitazione dell'account
 * @param approvalStatus stato di approvazione
 * @param createdAt istante di creazione
 * @param firstName nome anagrafico dell'utente
 * @param lastName cognome dell'utente
 * @see it.uniupo.pissir.darts.user.model.Role
 *  
 */
public record User(long id, String username, String email, String passwordHash, String salt,
                    Role role, boolean enabled, ApprovalStatus approvalStatus, Instant createdAt,
                    String firstName, String lastName) {

    /**
     * Inizializza {@code User} con i dati e le dipendenze indicati.
     *
     * @param id identificativo dell'utente
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param passwordHash hash della password
     * @param salt sale usato nel calcolo dell'hash
     * @param role ruolo dell'utente
     * @param enabled indicatore di abilitazione dell'account
     * @param approvalStatus stato di approvazione
     * @param createdAt istante di creazione
     * @see it.uniupo.pissir.darts.user.model.Role
     *  
     */
    public User(long id, String username, String email, String passwordHash, String salt,
                Role role, boolean enabled, ApprovalStatus approvalStatus, Instant createdAt) {
        this(id, username, email, passwordHash, salt, role, enabled, approvalStatus, createdAt, null, null);
    }

    /**
     * Conta ai fini statistiche per l'utente.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public boolean contaAiFiniStatistiche() {
        return enabled && approvalStatus == ApprovalStatus.APPROVATO;
    }
}
