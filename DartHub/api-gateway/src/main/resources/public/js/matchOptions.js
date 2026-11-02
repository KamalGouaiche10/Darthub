/**
 * Seleziona le regole associate al tipo di gioco indicato.
 *
 * @param {Array<Object>} regole Regole disponibili.
 * @param {(number|string)} gameTypeId Identificativo del tipo di gioco.
 * @returns {Array<Object>} Regole del gioco selezionato.
 * 
 */
export function variantiPerGioco(regole, gameTypeId) {
  return regole.filter((regola) => String(regola.gameTypeId) === String(gameTypeId));
}

/**
 * Cerca l’indirizzo del locale selezionato.
 *
 * @param {Array<Object>} locali Locali da consultare.
 * @param {(number|string)} localeId Identificativo del locale.
 * @returns {string} Indirizzo del locale, oppure il messaggio di indisponibilità.
 * 
 */
export function indirizzoDelLocale(locali, localeId) {
  return locali.find((locale) => String(locale.id) === String(localeId))?.indirizzo?.trim()
    || "Indirizzo non disponibile";
}
