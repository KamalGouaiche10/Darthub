/**
 * Sposta il modulo in una finestra di modifica con pulsante di chiusura.
 *
 * @param {HTMLFormElement} form Modulo da configurare.
 * @param {string} titolo Titolo della finestra.
 * @param {HTMLElement} [destinazione] Elemento in cui inserire la finestra.
 * @returns {HTMLDialogElement} Finestra contenente il modulo.
 * 
 */
export function creaDialogoModifica(form, titolo, destinazione = form.parentElement) {
  const dialogo = document.createElement("dialog");
  dialogo.className = "admin-manage-dialog admin-edit-dialog";
  dialogo.setAttribute("aria-label", titolo);

  const chiudi = document.createElement("button");
  chiudi.type = "button";
  chiudi.className = "admin-dialog-close";
  chiudi.setAttribute("aria-label", "Chiudi");
  chiudi.textContent = "×";
  chiudi.addEventListener("click", () => dialogo.close());

  const contenuto = document.createElement("div");
  contenuto.className = "admin-dialog-content";
  const intestazione = document.createElement("h3");
  intestazione.textContent = titolo;
  contenuto.append(intestazione);

  if (destinazione === form.parentElement) form.before(dialogo);
  else destinazione.append(dialogo);
  form.hidden = false;
  contenuto.append(form);
  dialogo.append(chiudi, contenuto);
  return dialogo;
}
