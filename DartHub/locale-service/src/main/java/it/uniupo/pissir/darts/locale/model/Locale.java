package it.uniupo.pissir.darts.locale.model;

import java.time.Instant;

/**
 * Riga della tabella locali.
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
 * @see it.uniupo.pissir.darts.locale.model.LocaleType
 *  
 */
public record Locale(long id, String nome, String indirizzo, LocaleType tipo, long ownerUserId,
                      LocaleStatus stato, Instant createdAt) {

    /**
     * Conta ai fini statistiche per il locale.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public boolean contaAiFiniStatistiche() {
        return stato == LocaleStatus.APPROVATO;
    }
}
