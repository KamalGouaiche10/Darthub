package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;
import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PATCH /bersagli/{id/stato} verso locale-service: IN_USO all'avvio della partita, DISPONIBILE a fine/annullamento. 
 */
public final class DartboardStatusClient {

    private static final Logger LOG = LoggerFactory.getLogger(DartboardStatusClient.class);

    private final ServiceHttpClient httpClient;
    private final String localeServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

    /**
     * Inizializza {@code DartboardStatusClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param localeServiceBaseUrl indirizzo di base del locale servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public DartboardStatusClient(ServiceHttpClient httpClient, String localeServiceBaseUrl,
                                  ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.localeServiceBaseUrl = localeServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Imposta in uso per il componente.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     *  
     */
    public void setInUso(long dartboardId, long localeId, long gameTypeId) {
        httpClient.post(localeServiceBaseUrl + "/edge-devices/bersagli/" + dartboardId + "/prenota",
                new ReserveRequest(localeId, gameTypeId), serviceTokenClient.token(), null);
    }

    /**
     * Verifica che il bersaglio appartenga al locale e al gioco richiesti.
     *
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @throws BusinessRuleException se viene rilevato l'errore «Il bersaglio non appartiene al locale o al tipo di gioco scelto»
     *  
     */
    public void requireCompatible(long dartboardId, long localeId, long gameTypeId) {
        Dartboard board = httpClient.get(localeServiceBaseUrl + "/bersagli/" + dartboardId,
                serviceTokenClient.token(), Dartboard.class);
        if (board.localeId() != localeId || board.gameTypeId() != gameTypeId) {
            throw new BusinessRuleException("BERSAGLIO_NON_COMPATIBILE",
                    "Il bersaglio non appartiene al locale o al tipo di gioco scelto");
        }
    }

    /**
     * Raccoglie i dati di bersaglio.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     *  
     */
    private record Dartboard(long id, long localeId, long gameTypeId) { }

    /**
     * Verifica se l'utente è proprietario del locale.
     *
     * @param localeId identificativo del locale
     * @param userId identificativo dell'utente
     * @return {@code true} se l'utente è proprietario del locale; {@code false} altrimenti
     *  
     */
    public boolean isLocaleOwner(long localeId, long userId) {
        Locale locale = httpClient.get(localeServiceBaseUrl + "/locali/" + localeId,
                serviceTokenClient.token(), Locale.class);
        return locale.ownerUserId() == userId;
    }

    /**
     * Raccoglie i dati di locale.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del locale
     * @param ownerUserId identificativo del proprietario del locale
     *  
     */
    private record Locale(long id, long ownerUserId) { }

    /**
     * Imposta disponibile per il componente.
     *
     * @param dartboardId identificativo del bersaglio
     *  
     */
    public void setDisponibile(long dartboardId) {
        setStato(dartboardId, "DISPONIBILE");
    }

    /**
     * Imposta stato per il componente.
     *
     * @param dartboardId identificativo del bersaglio
     * @param stato stato da impostare o verificare
     *  
     */
    private void setStato(long dartboardId, String stato) {
        try {
            httpClient.patch(localeServiceBaseUrl + "/bersagli/" + dartboardId + "/stato",
                    new StatoRequest(stato), serviceTokenClient.token(), null);
        } catch (RuntimeException e) {

            LOG.warn("Aggiornamento dello stato del bersaglio {} a {} fallito: {}", dartboardId, stato, e.getMessage());
        }
    }

    /**
     * Raccoglie i dati della richiesta relativa a stato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param stato stato da impostare o verificare
     *  
     */
    private record StatoRequest(String stato) {
    }

    /**
     * Raccoglie i dati della richiesta relativa a reserve.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     *  
     */
    private record ReserveRequest(long localeId, long gameTypeId) {
    }
}
