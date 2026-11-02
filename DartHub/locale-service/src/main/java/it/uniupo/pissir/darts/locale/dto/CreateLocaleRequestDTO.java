package it.uniupo.pissir.darts.locale.dto;

/**
 * Corpo di POST /locali (PLATFORM_ADMIN, che assegna il proprietario).
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param nome nome della risorsa
 * @param indirizzo indirizzo del locale
 * @param tipo tipo della risorsa o dell'evento
 * @param ownerUserId identificativo del proprietario del locale
 *  
 */
public record CreateLocaleRequestDTO(String nome, String indirizzo, String tipo, Long ownerUserId) {
}
