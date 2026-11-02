/**
 * Crea una finestra e ripristina il focus sull’elemento precedente alla chiusura.
 *
 * @param {string} titolo Titolo della finestra.
 * @param {string} messaggio Messaggio da mostrare all’utente.
 * @returns {Object} Finestra e contenitore del suo contenuto.
 * 
 */
function creaDialogo(titolo, messaggio) {
  const dialog = document.createElement("dialog");
  dialog.className = "ui-dialog";
  dialog.setAttribute("aria-labelledby", "ui-dialog-title");

  const contenuto = document.createElement("div");
  contenuto.className = "ui-dialog-content";
  const heading = document.createElement("h2");
  heading.id = "ui-dialog-title";
  heading.textContent = titolo;
  const description = document.createElement("p");
  description.textContent = messaggio;
  contenuto.append(heading, description);
  dialog.append(contenuto);
  document.body.append(dialog);
  const precedente = document.activeElement;
  dialog.addEventListener("close", () => {
    dialog.remove();
    if (precedente?.isConnected) precedente.focus();
  }, { once: true });
  return { dialog, contenuto };
}

/**
 * Aggiunge alla finestra i pulsanti di annullamento e conferma.
 *
 * @param {HTMLElement} dialog Elemento a cui aggiungere i pulsanti.
 * @param {string} principale Etichetta del pulsante principale.
 * @param {string} [annulla] Etichetta del pulsante di annullamento.
 * @returns {Object} Pulsanti cancel e submit.
 * 
 */
function azioni(dialog, principale, annulla = "Annulla") {
  const barra = document.createElement("div");
  barra.className = "ui-dialog-actions";
  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "secondario";
  cancel.textContent = annulla;
  const submit = document.createElement("button");
  submit.type = "submit";
  submit.textContent = principale;
  barra.append(cancel, submit);
  dialog.append(barra);
  return { cancel, submit };
}

/**
 * Mostra una finestra di conferma e attende la scelta dell’utente.
 *
 * @param {string} messaggio Messaggio da mostrare all’utente.
 * @param {string} [titolo] Titolo della finestra.
 * @returns {Promise<boolean>} True se l’utente conferma, false se annulla.
 * 
 */
export function conferma(messaggio, titolo = "Conferma operazione") {
  return new Promise((resolve) => {
    const { dialog } = creaDialogo(titolo, messaggio);
    const form = document.createElement("form");
    form.method = "dialog";
    const { cancel, submit } = azioni(form, "Conferma");
    submit.value = "confirm";
    cancel.addEventListener("click", () => dialog.close("cancel"));
    dialog.append(form);
    dialog.addEventListener("close", () => resolve(dialog.returnValue === "confirm"), { once: true });
    dialog.showModal();
    cancel.focus();
  });
}

/**
 * Mostra un campo URL e richiama la conferma durante l’invio del modulo.
 *
 * @param {Object} opzioni Opzioni della funzione.
 * @param {string} opzioni.titolo Titolo della finestra.
 * @param {string} opzioni.messaggio Messaggio da mostrare all’utente.
 * @param {string} opzioni.etichetta Etichetta da visualizzare.
 * @param {*} [opzioni.valore] Valore da elaborare.
 * @param {Function} opzioni.conferma Funzione chiamata con l’URL inserito durante l’invio del modulo.
 * 
 */
export function chiediTesto({ titolo, messaggio, etichetta, valore = "", conferma: salva }) {
  const { dialog, contenuto } = creaDialogo(titolo, messaggio);
  const form = document.createElement("form");
  form.className = "ui-dialog-form";
  form.noValidate = true;
  const label = document.createElement("label");
  label.htmlFor = "ui-dialog-input";
  label.textContent = etichetta;
  const input = document.createElement("input");
  input.id = "ui-dialog-input";
  input.type = "url";
  input.required = true;
  input.value = valore;
  input.autocomplete = "url";
  const error = document.createElement("p");
  error.className = "ui-dialog-error";
  error.setAttribute("role", "alert");
  error.hidden = true;
  const { cancel } = azioni(form, "Apri");
  form.prepend(label, input, error);
  contenuto.append(form);
  cancel.addEventListener("click", () => dialog.close("cancel"));
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    try {
      salva(input.value.trim());
      dialog.close("confirm");
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
      input.focus();
    }
  });
  dialog.showModal();
  input.focus();
  input.select();
}
