package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di PUT /locali/{id}.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param indirizzo indirizzo del locale
 * @param tipo tipo della risorsa o dell'evento
 *  
 */
public record UpdateLocaleRequestDTO(String nome, String indirizzo, String tipo) {
}
