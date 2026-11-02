package it.uniupo.pissir.darts.locale.dto;

/**
 * Rappresentazione pubblica di un bersaglio.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param identificativoLocale identificativo del bersaglio nel locale
 * @param gameTypeId identificativo del gioco
 * @param stato stato da impostare o verificare
 * @param createdAt istante di creazione
 * @param prenotabile indicatore di disponibilitÃ  dell'appuntamento alla prenotazione
 * @param statoEdge stato del dispositivo edge
 *  
 */
public record DartboardDTO(long id, long localeId, String identificativoLocale, long gameTypeId,
                            String stato, String createdAt, boolean prenotabile, String statoEdge) {
}
