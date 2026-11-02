package it.uniupo.pissir.darts.tournament.service;

import com.google.gson.reflect.TypeToken;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.common.security.ServiceTokenClient;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Verifica l'esistenza di un locale e ne recupera i bersagli, chiamando locale-service: ScheduleGenerator ha bisogno di un dartboardId per ogni partita del calendario.
 *
 * @see java.lang.reflect.Type
 *  
 */
public final class LocaleClient {

    private static final Type DARTBOARD_PAGE_TYPE = TypeToken.getParameterized(PagedResult.class, DartboardDTO.class).getType();

    private final ServiceHttpClient httpClient;
    private final String localeServiceBaseUrl;
    private final ServiceTokenClient serviceTokenClient;

    /**
     * Inizializza {@code LocaleClient} con i dati e le dipendenze indicati.
     *
     * @param httpClient componente {@link it.uniupo.pissir.darts.common.http.ServiceHttpClient} utilizzato dall'operazione
     * @param localeServiceBaseUrl indirizzo di base del locale servizio
     * @param serviceTokenClient componente {@link it.uniupo.pissir.darts.common.security.ServiceTokenClient} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.http.ServiceHttpClient
     *  
     */
    public LocaleClient(ServiceHttpClient httpClient, String localeServiceBaseUrl, ServiceTokenClient serviceTokenClient) {
        this.httpClient = httpClient;
        this.localeServiceBaseUrl = localeServiceBaseUrl;
        this.serviceTokenClient = serviceTokenClient;
    }

    /**
     * Verifica il requisito exists prima di eseguire l'operazione.
     *
     * @param localeId identificativo del locale
     *  
     */
    public void requireExists(long localeId) {
        httpClient.get(localeServiceBaseUrl + "/locali/" + localeId, serviceTokenClient.token(), Object.class);
    }

    /**
     * Recupera il nome del partecipante o della risorsa indicata.
     *
     * @param localeId identificativo del locale
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    public String nome(long localeId) {
        try {
            LocaleDTO locale = httpClient.get(localeServiceBaseUrl + "/locali/" + localeId,
                    serviceTokenClient.token(), LocaleDTO.class);
            return locale.nome() == null || locale.nome().isBlank() ? "Locale #" + localeId : locale.nome();
        } catch (RuntimeException e) {
            return "Locale #" + localeId;
        }
    }

    /**
     * Raccoglie i dati di locale.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param nome nome della risorsa
     *  
     */
    private record LocaleDTO(String nome) { }

    /**
     * Seleziona un bersaglio associato al gioco e al locale richiesti.
     *
     * @param localeId identificativo del locale
     * @param gameTypeId identificativo del gioco
     * @return il valore numerico calcolato o letto
     *  
     */
    public long unBersaglioCompatibile(long localeId, long gameTypeId) {
        PagedResult<DartboardDTO> pagina = httpClient.get(
                localeServiceBaseUrl + "/locali/" + localeId + "/bersagli?size=100", serviceTokenClient.token(), DARTBOARD_PAGE_TYPE);
        List<DartboardDTO> bersagli = pagina.contenuto();
        java.util.Optional<DartboardDTO> compatible = bersagli.stream()
                .filter(b -> gameTypeId < 0 || b.gameTypeId() == gameTypeId).findFirst();
        if (compatible.isEmpty()) {
            throw new it.uniupo.pissir.darts.common.exception.BusinessRuleException("LOCALE_SENZA_BERSAGLI",
                    "Il locale " + localeId + " non ha bersagli compatibili con il gioco del torneo");
        }
        return compatible.get().id();
    }

    /**
     * Stessa forma di DartboardDTO in locale-service.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param id identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param identificativoLocale identificativo del bersaglio nel locale
     * @param gameTypeId identificativo del gioco
     * @param stato stato da impostare o verificare
     * @param createdAt istante di creazione
     *  
     */
    private record DartboardDTO(long id, long localeId, String identificativoLocale, long gameTypeId, String stato,
                                 String createdAt) {
    }
}
