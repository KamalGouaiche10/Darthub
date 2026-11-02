package it.uniupo.pissir.darts.user.model;

import java.time.Instant;

/**
 * Riga della tabella refresh_tokens: tokenHash è un digest SHA-256, mai il token in chiaro.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del token di aggiornamento
 * @param userId identificativo dell'utente
 * @param tokenHash hash del token
 * @param expiresAt istante di scadenza
 * @param revoked indicatore di revoca del token
 * @see java.time.Instant
 *  
 */
public record RefreshToken(long id, long userId, String tokenHash, Instant expiresAt, boolean revoked) {
}
