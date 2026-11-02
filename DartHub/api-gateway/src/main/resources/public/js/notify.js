
/**
 * Mostra un messaggio temporaneo nella pagina.
 *
 * @param {string} messaggio Messaggio da mostrare all’utente.
 * @param {string} [tipo] Tipo della notifica.
 * 
 */
export function notifica(messaggio, tipo = "successo") {
  const contenitore = document.getElementById("notifiche");
  const elemento = document.createElement("div");
  elemento.className = `notifica ${tipo}`;
  elemento.textContent = messaggio;
  contenitore.appendChild(elemento);
  setTimeout(() => elemento.remove(), 4000);
}

/**
 * Mostra una notifica con il messaggio dell’errore ricevuto.
 *
 * @param {*} errore Errore di cui mostrare il messaggio.
 * 
 */
export function notificaErrore(errore) {
  notifica(errore && errore.message ? errore.message : "Si è verificato un errore imprevisto", "errore");
}
