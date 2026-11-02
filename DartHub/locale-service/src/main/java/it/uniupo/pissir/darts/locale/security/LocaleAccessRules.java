package it.uniupo.pissir.darts.locale.security;

import it.uniupo.pissir.darts.common.exception.ForbiddenException;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.locale.model.Role;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;

/**
 * Ownership: un LOCALE_ADMIN può operare solo sui locali (e su bersagli/dispositivi edge che vi appartengono) con owner_user_id uguale al proprio userId; un PLATFORM_ADMIN opera su tutto. 
 */
public final class LocaleAccessRules {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private LocaleAccessRules() {
    }

    /**
     * Verifica il requisito proprietario o platform amministratore prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param ownerUserId identificativo del proprietario del locale
     * @throws ForbiddenException se viene rilevato l'errore «Puoi operare solo sui locali di tua proprietà»
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireOwnerOrPlatformAdmin(AuthenticatedUser user, long ownerUserId) {
        if (user.hasAnyRole(Role.PLATFORM_ADMIN.name())) {
            return;
        }
        if (!user.hasAnyRole(Role.LOCALE_ADMIN.name()) || user.userId() != ownerUserId) {
            throw new ForbiddenException("NON_PROPRIETARIO", "Puoi operare solo sui locali di tua proprietà");
        }
    }

    /**
     * Verifica che la risorsa sia visibile all'utente.
     *
     * @param user utente da utilizzare
     * @param locale locale a cui si riferiscono i dati
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireVisible(AuthenticatedUser user, Locale locale) {
        if (locale.stato() != LocaleStatus.APPROVATO) {
            requireOwnerOrPlatformAdmin(user, locale.ownerUserId());
        }
    }

    /**
     * Verifica il requisito proprietario o platform amministratore o servizio prima di eseguire l'operazione.
     *
     * @param user utente da utilizzare
     * @param ownerUserId identificativo del proprietario del locale
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    public static void requireOwnerOrPlatformAdminOrService(AuthenticatedUser user, long ownerUserId) {
        if (user.hasAnyRole("SERVICE")) {
            return;
        }
        requireOwnerOrPlatformAdmin(user, ownerUserId);
    }
}
