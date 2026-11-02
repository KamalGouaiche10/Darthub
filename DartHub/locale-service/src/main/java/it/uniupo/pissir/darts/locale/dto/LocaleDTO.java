package it.uniupo.pissir.darts.locale.dto;

/**
 * Rappresentazione pubblica di un locale.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del locale
 * @param nome nome della risorsa
 * @param indirizzo indirizzo del locale
 * @param tipo tipo della risorsa o dell'evento
 * @param ownerUserId identificativo del proprietario del locale
 * @param stato stato da impostare o verificare
 * @param createdAt istante di creazione
 *  
 */
public record LocaleDTO(long id, String nome, String indirizzo, String tipo, long ownerUserId, String stato,
                         String createdAt) {
}
