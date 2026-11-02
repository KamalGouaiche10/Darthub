/**
 * Converte un valore in testo e protegge i caratteri speciali HTML.
 *
 * @param {*} value Valore da inserire nel contenuto HTML.
 * @returns {string} Testo protetto, dopo la conversione del valore con String.
 */
export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[character]);
}

/**
 * Protegge un valore HTML trattando null e undefined come testo vuoto.
 *
 * @param {*} value Valore da inserire nel contenuto HTML.
 * @returns {string} Testo protetto, oppure stringa vuota se il valore è assente.
 */
export function escapeHtmlOrEmpty(value) {
  return escapeHtml(value ?? "");
}

