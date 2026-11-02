package it.uniupo.pissir.darts.common.security;

import java.time.Instant;

/**
 * Claim di un token JWT della piattaforma: sub (userId), username, role, iat, exp, più localeId e dartboardId per i token EDGE_DEVICE.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param sub identificativo del soggetto autenticato dal token
 * @param username nome dell'account
 * @param role ruolo dell'utente
 * @param iat istante di emissione del token
 * @param exp istante di scadenza del token
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 * @see java.time.Instant
 *  
 */
public record JwtClaims(String sub, String username, String role, Instant iat, Instant exp,
                         Long localeId, Long dartboardId) {

}
