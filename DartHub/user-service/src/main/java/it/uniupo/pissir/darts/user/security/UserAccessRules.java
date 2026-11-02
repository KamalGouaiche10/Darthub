package it.uniupo.pissir.darts.user.security;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.user.model.Role;

/**
 * Ruolo richiesto per rotta + ownership: self oppure PLATFORM_ADMIN. 
 */
public final class UserAccessRules {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private UserAccessRules() {
    }

    /**
     * Verifica il requisito self o platform amministratore prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param targetUserId identificativo dell'utente destinatario
     * @throws ForbiddenException se viene rilevato l'errore «Puoi operare solo sul tuo account»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireSelfOrPlatformAdmin(AuthenticatedUser user, long targetUserId) {
        if (user.userId() == targetUserId) {
            return;
        }
        if (!user.hasAnyRole(Role.PLATFORM_ADMIN.name())) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Puoi operare solo sul tuo account");
        }
    }

    /**
     * Verifica il requisito self o platform amministratore o servizio prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param targetUserId identificativo dell'utente destinatario
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireSelfOrPlatformAdminOrService(AuthenticatedUser user, long targetUserId) {
        if (user.hasAnyRole("SERVICE")) {
            return;
        }
        requireSelfOrPlatformAdmin(user, targetUserId);
    }

    /**
     * Verifica che la richiesta riguardi l'utente autenticato.
     *
     * @param user utente da utilizzare
     * @param targetUserId identificativo dell'utente destinatario
     * @throws ForbiddenException se viene rilevato l'errore «Puoi cambiare solo la tua password»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireSelf(AuthenticatedUser user, long targetUserId) {
        if (user.userId() != targetUserId) {
            throw new ForbiddenException("NON_AUTORIZZATO", "Puoi cambiare solo la tua password");
        }
    }
}
