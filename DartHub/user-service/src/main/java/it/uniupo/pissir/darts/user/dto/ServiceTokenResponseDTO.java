package it.uniupo.pissir.darts.user.dto;

/**
 * Risposta di POST /auth/service-token.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param accessToken token JWT di accesso alla sessione
 * @param tokenType tipo del token restituito
 * @param expiresInSeconds durata residua del token in secondi
 *  
 */
public record ServiceTokenResponseDTO(String accessToken, String tokenType, long expiresInSeconds) {
}
