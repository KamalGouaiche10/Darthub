package it.uniupo.pissir.darts.common.security;

/**
 * Identità risolta per la richiesta corrente: userId e ruolo.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param userId identificativo dell'utente
 * @param role ruolo dell'utente
 * @param localeId identificativo del locale
 * @param dartboardId identificativo del bersaglio
 *  
 */
public record AuthenticatedUser(long userId, String role, Long localeId, Long dartboardId) {

    /**
     * Verifica la presenza di almeno uno dei ruoli richiesti.
     *
     * @param roles ruoli da considerare
     * @return l'esito della condizione verificata
     *  
     */
    public boolean hasAnyRole(String... roles) {
        for (String candidate : roles) {
            if (role.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
