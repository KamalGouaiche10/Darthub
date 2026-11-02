/**
 * Abbina le richieste dei locali ai gestori e include gli account in attesa senza locale.
 *
 * @param {Array<Object>} gestori Account dei gestori da associare ai locali.
 * @param {Array<Object>} locali Locali da consultare.
 * @returns {Array<Object>} Righe con gestore e relativo locale, se presente.
 * 
 */
export function righeApprovazione(gestori, locali) {
  const perId = new Map(gestori.map((gestore) => [Number(gestore.id), gestore]));
  const proprietariConRichiesta = new Set(locali.map((locale) => Number(locale.ownerUserId)));
  const accountSenzaLocale = gestori
    .filter((gestore) => gestore.approvalStatus === "IN_ATTESA" && !proprietariConRichiesta.has(Number(gestore.id)))
    .map((gestore) => ({ gestore, locale: null }));
  return [...accountSenzaLocale, ...locali.map((locale) => ({
    gestore: perId.get(Number(locale.ownerUserId)), locale,
  }))];
}
