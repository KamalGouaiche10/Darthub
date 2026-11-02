package it.uniupo.pissir.darts.user.dto;

/**
 * Corpo di POST /auth/refresh e POST /auth/logout.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param refreshToken token di aggiornamento della sessione
 *  
 */
public record RefreshRequestDTO(String refreshToken) {
}
