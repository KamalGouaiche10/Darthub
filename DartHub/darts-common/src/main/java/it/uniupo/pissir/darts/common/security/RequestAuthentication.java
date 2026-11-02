package it.uniupo.pissir.darts.common.security;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;

/**
 * Risolve l'identità della richiesta e verifica i ruoli richiesti. 
 */
public final class RequestAuthentication {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private RequestAuthentication() {
    }

    /**
     * Risolve l'identità autenticata dal token della richiesta.
     *
     * @param authorization valore dell'header di autorizzazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.AuthenticatedUser} prodotto o recuperato
     * @throws UnauthorizedException se viene rilevato l'errore «Token JWT assente»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static AuthenticatedUser resolve(String authorization, JwtTokenService jwtTokenService) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Token JWT assente");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        JwtClaims claims = jwtTokenService.validateAndExtractClaims(token);
        return new AuthenticatedUser(Long.parseLong(claims.sub()), claims.role(), claims.localeId(), claims.dartboardId());
    }

    /**
     * Richiede almeno uno dei ruoli autorizzati all'operazione.
     *
     * @param user utente da utilizzare
     * @param allowedRoles ruoli autorizzati all'operazione
     * @throws ForbiddenException se viene rilevato l'errore «Ruolo insufficiente per l'operazione richiesta»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireAnyRole(AuthenticatedUser user, String... allowedRoles) {
        if (!user.hasAnyRole(allowedRoles)) {
            throw new ForbiddenException("RUOLO_INSUFFICIENTE", "Ruolo insufficiente per l'operazione richiesta");
        }
    }

    /**
     * Verifica il requisito own bersaglio prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param dartboardId identificativo del bersaglio
     * @throws ForbiddenException se viene rilevato l'errore «Il dispositivo non è associato a questo bersaglio»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireOwnDartboard(AuthenticatedUser user, long dartboardId) {
        if (user.dartboardId() == null || user.dartboardId() != dartboardId) {
            throw new ForbiddenException("BERSAGLIO_NON_PROPRIO", "Il dispositivo non è associato a questo bersaglio");
        }
    }
}
