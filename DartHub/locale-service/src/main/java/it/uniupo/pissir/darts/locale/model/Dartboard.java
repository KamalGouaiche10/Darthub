package it.uniupo.pissir.darts.locale.model;

import java.time.Instant;

/**
 * Riga della tabella bersagli.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo del bersaglio
 * @param localeId identificativo del locale
 * @param identificativoLocale identificativo del bersaglio nel locale
 * @param gameTypeId identificativo del gioco
 * @param stato stato da impostare o verificare
 * @param createdAt istante di creazione
 * @see it.uniupo.pissir.darts.locale.model.DartboardStatus
 *  
 */
public record Dartboard(long id, long localeId, String identificativoLocale, long gameTypeId,
                         DartboardStatus stato, Instant createdAt) {
}
