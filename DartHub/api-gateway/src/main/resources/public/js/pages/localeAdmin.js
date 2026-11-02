import { escapeHtmlOrEmpty as escapeHtml } from "../html.js";
import { Api, queryString } from "../api.js";
import { Auth } from "../auth.js";
import { notifica, notificaErrore } from "../notify.js";
import { conferma } from "../uiDialog.js";
import { creaDialogoModifica } from "../manageDialog.js";
import { caricaTutteLePagine } from "../../components/dataTable.js";
import { renderStatisticheLocali } from "../localeStatistics.js";
import { demoEdgeLocale, registraEdge } from "../edgeRegistration.js";

const TIPI_LOCALE = ["PUBBLICO", "PRIVATO"];
const STATI_BERSAGLIO = ["DISPONIBILE", "MANUTENZIONE", "OFFLINE"];
const CLASSE_STATO_BERSAGLIO = { DISPONIBILE: "badge-ok", IN_USO: "badge-warn", MANUTENZIONE: "badge-danger", OFFLINE: "badge-muted" };
const CLASSE_STATO_EDGE = { ONLINE: "badge-ok", OFFLINE: "badge-danger" };
const SEZIONI = [
  { id: "locali", nome: "I miei locali" },
  { id: "bersagli", nome: "Bersagli" },
  { id: "dispositivi", nome: "Dispositivi edge" },
  { id: "partite", nome: "Partite attive" },
  { id: "statistiche", nome: "Statistiche" },
];
const descrizioneStato = { APPROVATO: "Approvato", IN_ATTESA: "In attesa di approvazione", RIFIUTATO: "Rifiutato" };
const classeStato = { APPROVATO: "badge-ok", IN_ATTESA: "badge-warn", RIFIUTATO: "badge-danger" };
let giochiCache = null;

/**
 * Prepara la struttura della pagina di gestione dei locali.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {HTMLElement} Contenitore della pagina.
 * 
 */
function creaLayout(container) {
  container.innerHTML = `
    <div class="admin-layout locale-admin-layout">
      <aside class="admin-sidebar" aria-label="Menu gestione locali">
        <div class="admin-sidebar-heading">CONSOLE / LOCALI</div>
        <nav class="admin-nav" aria-label="Sezioni gestione locali">
          ${SEZIONI.map((sezione, indice) => `
            <button type="button" class="admin-nav-link" data-locale-section="${sezione.id}" aria-controls="locale-${sezione.id}">
              <span class="admin-nav-index">${String(indice + 1).padStart(2, "0")}</span>
              <span>${sezione.nome}</span><span class="admin-nav-arrow" aria-hidden="true">→</span>
            </button>`).join("")}
        </nav>
        <div class="admin-sidebar-foot">DartHub <span>·</span> Locali</div>
      </aside>
      <div class="admin-content">
        <header class="admin-header">
          <span class="eyebrow"><span class="eyebrow-dot"></span> GESTIONE LOCALI</span>
          <h1>I miei locali</h1>
          <p>Gestisci i tuoi locali, i bersagli e le richieste di approvazione.</p>
        </header>
        <div class="admin-panels">
          ${SEZIONI.map((sezione) => `<section id="locale-${sezione.id}" class="admin-panel" hidden></section>`).join("")}
        </div>
      </div>
    </div>`;
  const layout = container.querySelector(".locale-admin-layout");
  /**
   * Mostra la sezione amministrativa selezionata e aggiorna la navigazione. @param {(number|string)} id Identificativo della risorsa.
   * 
   */
  function attivaSezione(id) {
    for (const sezione of SEZIONI) {
      const attiva = sezione.id === id;
      layout.querySelector(`#locale-${sezione.id}`).hidden = !attiva;
      const pulsante = layout.querySelector(`[data-locale-section="${sezione.id}"]`);
      pulsante.classList.toggle("attivo", attiva);
      if (attiva) pulsante.setAttribute("aria-current", "page");
      else pulsante.removeAttribute("aria-current");
    }
  }
  layout.querySelector(".admin-nav").addEventListener("click", (evento) => {
    const pulsante = evento.target.closest("[data-locale-section]");
    if (pulsante) attivaSezione(pulsante.dataset.localeSection);
  });
  attivaSezione("locali");
  return layout;
}

/**
 * Carica i locali appartenenti all’utente indicato.
 *
 * @param {(number|string)} userId Identificativo dell’utente.
 * @returns {Promise<Array<Object>>} Locali restituiti dalla pagina richiesta.
 * 
 */
async function caricaPropriLocali(userId) {
  const risposta = await caricaTutteLePagine((page, size) =>
    Api.get("/locali" + queryString({ ownerUserId: userId, page, size })));
  return risposta.contenuto;
}

/**
 * Costruisce il riepilogo HTML di un locale.
 *
 * @param {Object} locale Dati del locale.
 * @returns {string} Scheda del locale.
 * 
 */
function schedaLocale(locale) {
  return `<div class="card locale-admin-summary-card" data-locale-creato="${locale.id}">
    <div class="locale-admin-summary-heading">
      <h3>${escapeHtml(locale.nome)}</h3>
      <details class="locale-admin-summary-menu">
        <summary aria-label="Azioni per il locale ${escapeHtml(locale.nome)}" title="Azioni locale">⋮</summary>
        <div class="locale-admin-summary-menu-panel">
          <button type="button" class="secondario" data-modifica-locale>Modifica</button>
          <button type="button" class="pericolo" data-elimina-locale>Elimina</button>
        </div>
      </details>
    </div>
    <p>${escapeHtml(locale.indirizzo || "Indirizzo non indicato")}</p>
    <span class="badge ${classeStato[locale.stato] || "badge-muted"}">${descrizioneStato[locale.stato] || escapeHtml(locale.stato)}</span>
    <span class="badge badge-muted">${escapeHtml(locale.tipo)}</span>
    <form class="locale-admin-edit-form" hidden>
      <label>Nome<input name="nome" required></label>
      <label>Indirizzo<input name="indirizzo"></label>
      <label>Tipo<select name="tipo">${TIPI_LOCALE.map((tipo) => `<option value="${tipo}">${tipo}</option>`).join("")}</select></label>
      <div class="locale-admin-form-actions">
        <button type="submit">Salva modifiche</button>
        <button type="button" class="secondario" data-annulla-modifica-locale>Annulla</button>
      </div>
    </form>
  </div>`;
}

/**
 * Prepara la pagina di gestione dei locali e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  container.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let datiUtente;
  let locali;
  try {
    [datiUtente, locali] = await Promise.all([
      Api.get(`/users/${user.userId}`), caricaPropriLocali(user.userId),
    ]);
  } catch (e) {
    container.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
    return;
  }

  const layout = creaLayout(container);
  const puoCreare = datiUtente.approvalStatus !== "RIFIUTATO";
  const localiOperativi = locali.filter((l) => l.stato === "APPROVATO");
  layout.querySelector('[data-locale-section="statistiche"]').addEventListener("click", () =>
    renderStatisticheLocali(layout.querySelector("#locale-statistiche"), localiOperativi));
  const localiConBersagli = locali.filter((l) => l.stato !== "RIFIUTATO" && puoCreare);
  const avvisoAccount = datiUtente.approvalStatus === "IN_ATTESA"
    ? '<div class="stato-vuoto">Il tuo account è in attesa di approvazione. Puoi inviare le richieste per i tuoi locali e preparare i bersagli.</div>'
    : datiUtente.approvalStatus === "RIFIUTATO"
      ? '<div class="stato-errore">La tua richiesta di registrazione come gestore di locale è stata rifiutata.</div>'
      : "";

  layout.querySelector("#locale-locali").innerHTML = `
    <div class="locale-admin-heading"><h2>I miei locali</h2>
      ${puoCreare ? '<button id="btn-crea-locale" type="button" aria-controls="creazione-locale" aria-expanded="false">Crea nuovo locale</button>' : ""}
    </div>${avvisoAccount}
    <div id="conferma-locale" class="stato-successo" role="status" hidden></div>
    <div id="elenco-locali">${locali.length ? locali.map(schedaLocale).join("") : '<div class="stato-vuoto">Non possiedi ancora nessun locale.</div>'}</div>
    ${puoCreare ? `<dialog id="creazione-locale" class="admin-manage-dialog admin-edit-dialog locale-admin-create-dialog" aria-labelledby="creazione-locale-title">
      <button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button>
      <div class="admin-dialog-content">
        <h3 id="creazione-locale-title">Crea nuovo locale</h3>
        <p>Ogni nuovo locale richiede l'approvazione dell'amministratore di piattaforma.</p>
        <form id="form-nuovo-locale">
          <label for="nl-nome">Nome</label><input id="nl-nome" name="nome" required>
          <label for="nl-indirizzo">Indirizzo</label><input id="nl-indirizzo" name="indirizzo">
          <label for="nl-tipo">Tipo</label>
          <select id="nl-tipo" name="tipo">${TIPI_LOCALE.map((t) => `<option value="${t}">${t}</option>`).join("")}</select>
          <div id="errore-nuovo-locale" class="stato-errore" role="alert" hidden></div>
          <div class="locale-admin-form-actions"><button type="submit">Salva locale</button>
            <button type="button" id="annulla-nuovo-locale" class="secondario">Annulla</button></div>
        </form>
      </div>
    </dialog>` : ""}`;

  layout.querySelector("#locale-bersagli").innerHTML = `<h2>Bersagli</h2>
    <p>Prima crea il bersaglio, poi registra il dispositivo edge che lo collega alla piattaforma.</p>
    ${localiConBersagli.length ? localiConBersagli.map((l) => `<div class="card locale-admin-detail-card">
      <h3>${escapeHtml(l.nome)}</h3>
      <span class="badge ${classeStato[l.stato] || "badge-muted"}">${descrizioneStato[l.stato]}</span>
      <div class="griglia" id="bersagli-${l.id}"><div class="stato-caricamento">Caricamento…</div></div>
      <form class="riga-form form-nuovo-bersaglio" data-locale-id="${l.id}" style="margin-top: 0.6rem;">
        <div><label for="nb-id-${l.id}">Identificativo per ${escapeHtml(l.nome)}</label><input id="nb-id-${l.id}" name="identificativo" required></div>
        <div><label for="nb-gioco-${l.id}">Gioco</label><select id="nb-gioco-${l.id}" name="gioco" required></select></div>
        <div><button type="submit">+ Bersaglio a ${escapeHtml(l.nome)}</button></div>
      </form>
    </div>`).join("") : '<div class="stato-vuoto">Nessun locale disponibile per i bersagli.</div>'}`;

  layout.querySelector("#locale-dispositivi").innerHTML = `
    <div class="locale-admin-heading"><h2>Dispositivi edge</h2>
      <button id="btn-crea-edge" type="button" aria-controls="form-crea-edge" aria-expanded="false" disabled>Crea dispositivo edge</button>
    </div>
    <p class="game-admin-intro">Ogni dispositivo edge si collega a un bersaglio e trasmette i tiri alla piattaforma. La registrazione genera un token da configurare sul dispositivo. Lo stato resta OFFLINE finché l'edge non invia il primo segnale di presenza.</p>
    <p id="edge-disponibilita" class="stato-vuoto" hidden></p>
    <form id="form-crea-edge" class="card riga-form locale-admin-edge-form" hidden>
      <div><label>Locale<select name="localeId" required></select></label></div>
      <div><label>Bersaglio<select name="dartboardId" required></select></label></div>
      <div><label>Numero seriale<input name="serialNumber" required maxlength="120" placeholder="Es. EDGE-001"></label></div>
      <div class="admin-form-action"><button type="submit">Registra</button></div>
      <div class="admin-form-action"><button type="button" class="secondario" data-annulla-edge>Annulla</button></div>
    </form>
    <div id="edge-token-creato" class="locale-admin-edge-token" role="status" hidden></div>
    ${localiOperativi.length ? localiOperativi.map((l) => `<div class="card locale-admin-detail-card">
      <h3>${escapeHtml(l.nome)}</h3>
      <div id="edge-${l.id}"><div class="stato-caricamento">Caricamento…</div></div>
    </div>`).join("") : '<div class="stato-vuoto">Nessun locale approvato.</div>'}`;
  layout.querySelector("#locale-partite").innerHTML = `<h2>Partite attive</h2>
    ${localiOperativi.length ? localiOperativi.map((l) => `<div class="card locale-admin-detail-card">
      <h3>${escapeHtml(l.nome)}</h3>
      <div id="partite-${l.id}"><div class="stato-caricamento">Caricamento…</div></div>
    </div>`).join("") : '<div class="stato-vuoto">Nessun locale approvato.</div>'}`;

  const aggiornaDisponibilitaEdge = configuraCreazioneEdge(layout, localiOperativi);

  const apriCreazione = layout.querySelector("#btn-crea-locale");
  const pannelloCreazione = layout.querySelector("#creazione-locale");
  apriCreazione?.addEventListener("click", () => {
    pannelloCreazione.showModal();
    apriCreazione.setAttribute("aria-expanded", "true");
    pannelloCreazione.querySelector("#nl-nome").focus();
  });
  pannelloCreazione?.addEventListener("close", () => {
    pannelloCreazione.querySelector("form").reset();
    pannelloCreazione.querySelector("#errore-nuovo-locale").hidden = true;
    apriCreazione.setAttribute("aria-expanded", "false");
  });
  pannelloCreazione?.querySelector(".admin-dialog-close").addEventListener("click", () => pannelloCreazione.close());
  layout.querySelector("#annulla-nuovo-locale")?.addEventListener("click", () => pannelloCreazione.close());
  layout.querySelector("#form-nuovo-locale")?.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const bottone = form.querySelector('button[type="submit"]');
    const errore = form.querySelector("#errore-nuovo-locale");
    errore.hidden = true;
    const nome = form.elements.namedItem("nome").value.trim();
    if (!nome) {
      errore.textContent = "Inserisci il nome del locale.";
      errore.hidden = false;
      form.elements.namedItem("nome").focus();
      return;
    }
    bottone.disabled = true;
    try {
      const creato = await Api.post("/locali", {
        nome, indirizzo: form.elements.namedItem("indirizzo").value.trim(),
        tipo: form.elements.namedItem("tipo").value,
      });
      locali.push(creato);
      const elenco = layout.querySelector("#elenco-locali");
      elenco.querySelector(".stato-vuoto")?.remove();
      elenco.insertAdjacentHTML("afterbegin", schedaLocale(creato));
      const conferma = layout.querySelector("#conferma-locale");
      conferma.textContent = `Locale "${creato.nome}" salvato. La richiesta è in attesa di approvazione.`;
      conferma.hidden = false;
      if (pannelloCreazione.open) pannelloCreazione.close();
    } catch (e) {
      errore.textContent = `Impossibile salvare il locale: ${e.message || "riprova più tardi"}`;
      errore.hidden = false;
    } finally {
      bottone.disabled = false;
    }
  });

  const elencoLocali = layout.querySelector("#elenco-locali");
  elencoLocali.addEventListener("click", async (evento) => {
    const scheda = evento.target.closest("[data-locale-creato]");
    if (!scheda) return;
    const locale = locali.find((l) => String(l.id) === scheda.dataset.localeCreato);
    if (!locale) return;
    const menu = scheda.querySelector(".locale-admin-summary-menu");
    const form = scheda.querySelector(".locale-admin-edit-form");
    if (evento.target.closest("[data-modifica-locale]")) {
      menu.open = false;
      form.elements.namedItem("nome").value = locale.nome;
      form.elements.namedItem("nome").setCustomValidity("");
      form.elements.namedItem("indirizzo").value = locale.indirizzo || "";
      form.elements.namedItem("tipo").value = locale.tipo;
      const dialogo = form.closest("dialog") || creaDialogoModifica(form, `Modifica locale: ${locale.nome}`);
      dialogo.showModal();
      form.elements.namedItem("nome").focus();
    } else if (evento.target.closest("[data-annulla-modifica-locale]")) {
      form.closest("dialog").close();
      menu.querySelector("summary").focus();
    } else if (evento.target.closest("[data-elimina-locale]")) {
      menu.open = false;
      if (!await conferma(`Eliminare definitivamente il locale "${locale.nome}", i suoi bersagli e i dispositivi edge associati? L'operazione non è reversibile.`)) return;
      const bottone = scheda.querySelector("[data-elimina-locale]");
      bottone.disabled = true;
      try {
        await Api.del(`/locali/${locale.id}`);
        notifica("Locale eliminato");
        await render(container);
      } catch (e) {
        notificaErrore(e);
        bottone.disabled = false;
      }
    }
  });
  elencoLocali.addEventListener("submit", async (evento) => {
    const form = evento.target.closest(".locale-admin-edit-form");
    if (!form) return;
    evento.preventDefault();
    const scheda = form.closest("[data-locale-creato]");
    const locale = locali.find((l) => String(l.id) === scheda.dataset.localeCreato);
    if (!locale) return;
    const nome = form.elements.namedItem("nome").value.trim();
    if (!nome) {
      form.elements.namedItem("nome").setCustomValidity("Inserisci il nome del locale");
      form.elements.namedItem("nome").reportValidity();
      return;
    }
    const bottone = form.querySelector('[type="submit"]');
    bottone.disabled = true;
    try {
      await Api.put(`/locali/${locale.id}`, {
        nome, indirizzo: form.elements.namedItem("indirizzo").value.trim(),
        tipo: form.elements.namedItem("tipo").value,
      });
      notifica("Locale aggiornato");
      if (form.closest("dialog")?.open) form.closest("dialog").close();
      await render(container);
    } catch (e) {
      notificaErrore(e);
      bottone.disabled = false;
    }
  });
  elencoLocali.addEventListener("input", (evento) => {
    if (evento.target.matches('.locale-admin-edit-form [name="nome"]')) evento.target.setCustomValidity("");
  });

  for (const locale of localiConBersagli) {
    const form = layout.querySelector(`[data-locale-id="${locale.id}"]`);
    form.addEventListener("submit", async (evento) => {
      evento.preventDefault();
      const bottone = form.querySelector('button[type="submit"]');
      bottone.disabled = true;
      try {
        await Api.post(`/locali/${locale.id}/bersagli`, {
          identificativoLocale: form.identificativo.value.trim(),
          gameTypeId: Number(form.gioco.value),
        });
        notifica(`Bersaglio aggiunto a ${locale.nome}`);
        form.identificativo.value = "";
        await caricaBersagli(locale.id, aggiornaDisponibilitaEdge);
        if (locale.stato === "APPROVATO") await caricaEdgeDevices(locale.id, aggiornaDisponibilitaEdge);
        await aggiornaDisponibilitaEdge();
      } catch (e) {
        notificaErrore(e);
      } finally {
        bottone.disabled = false;
      }
    });
    caricaGiochiInSelect(form.gioco);
    caricaBersagli(locale.id, aggiornaDisponibilitaEdge);
  }
  for (const locale of localiOperativi) {
    caricaEdgeDevices(locale.id, aggiornaDisponibilitaEdge);
    caricaPartiteAttive(locale.id);
  }
}

/**
 * Carica i tipi di gioco nelle opzioni del selettore.
 *
 * @param {HTMLSelectElement} selectEl Selettore da popolare.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaGiochiInSelect(selectEl) {
  try {
    if (!giochiCache) {
      giochiCache = caricaTutteLePagine((page, size) =>
        Api.get("/giochi" + queryString({ page, size }))).then((risposta) => risposta.contenuto);
    }
    const giochi = await giochiCache;
    selectEl.innerHTML = giochi.map((g) => `<option value="${g.id}">${escapeHtml(g.nome)}</option>`).join("");
  } catch {
    giochiCache = null;
    selectEl.innerHTML = "";
  }
}

/**
 * Carica i bersagli del locale e aggiorna le relative opzioni o schede.
 *
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {Function} [aggiornaDisponibilita] Funzione da chiamare dopo una modifica alla disponibilità.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaBersagli(localeId, aggiornaDisponibilita = () => {}) {
  const zona = document.getElementById(`bersagli-${localeId}`);
  try {
    const risposta = await caricaTutteLePagine((page, size) =>
      Api.get(`/locali/${localeId}/bersagli` + queryString({ page, size })));
    zona.innerHTML = risposta.contenuto.length
      ? risposta.contenuto.map((b) => `<div class="card locale-admin-dartboard-card" data-dartboard-id="${b.id}">
          <div class="locale-admin-dartboard-heading">
            <strong>${escapeHtml(b.identificativoLocale)}</strong>
            <details class="locale-admin-dartboard-menu">
              <summary aria-label="Azioni per il bersaglio ${escapeHtml(b.identificativoLocale)}" title="Azioni bersaglio">⋮</summary>
              <div class="locale-admin-dartboard-menu-panel">
                <button type="button" class="secondario" data-modifica-bersaglio>Modifica</button>
                <button type="button" class="pericolo" data-elimina-bersaglio>Elimina</button>
              </div>
            </details>
          </div>
          <div style="margin: 0.4rem 0;"><span class="badge ${CLASSE_STATO_BERSAGLIO[b.stato] || "badge-muted"}">${escapeHtml(b.stato)}</span> · Edge ${escapeHtml(b.statoEdge || "ASSENTE")}</div>
          <select data-bersaglio="${b.id}" aria-label="Stato del bersaglio ${escapeHtml(b.identificativoLocale)}">
            ${b.stato === "IN_USO" ? '<option value="IN_USO" selected disabled>IN_USO · partita attiva o da verificare</option>' : ""}
            ${STATI_BERSAGLIO.map((s) => `<option value="${s}" ${s === b.stato ? "selected" : ""}>${s}</option>`).join("")}
          </select>
          <form class="riga-form locale-admin-edit-dartboard" hidden>
            <div><label>Identificativo<input name="identificativo" required></label></div>
            <div><label>Gioco<select name="gioco" required></select></label></div>
            <div><button type="submit">Salva</button></div>
            <div><button type="button" class="secondario" data-annulla-bersaglio>Annulla</button></div>
          </form>
        </div>`).join("")
      : '<div class="stato-vuoto">Nessun bersaglio inserito</div>';
    for (const bersaglio of risposta.contenuto) {
      const scheda = zona.querySelector(`[data-dartboard-id="${bersaglio.id}"]`);
      const menu = scheda.querySelector(".locale-admin-dartboard-menu");
      const form = scheda.querySelector(".locale-admin-edit-dartboard");
      const identificativo = form.elements.namedItem("identificativo");
      const gioco = form.elements.namedItem("gioco");
      const select = scheda.querySelector("[data-bersaglio]");
      scheda.querySelector("[data-modifica-bersaglio]").addEventListener("click", async () => {
        menu.open = false;
        identificativo.value = bersaglio.identificativoLocale;
        const dialogo = form.closest("dialog") || creaDialogoModifica(form, `Modifica bersaglio: ${bersaglio.identificativoLocale}`);
        dialogo.showModal();
        await caricaGiochiInSelect(gioco);
        if (!dialogo.open) return;
        gioco.value = String(bersaglio.gameTypeId);
        identificativo.focus();
      });
      form.querySelector("[data-annulla-bersaglio]").addEventListener("click", () => {
        form.closest("dialog").close();
        menu.querySelector("summary").focus();
      });
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const salva = form.querySelector('[type="submit"]');
        salva.disabled = true;
        try {
          await Api.put(`/bersagli/${bersaglio.id}`, {
            identificativoLocale: identificativo.value.trim(), gameTypeId: Number(gioco.value),
          });
          notifica("Bersaglio aggiornato");
          if (form.closest("dialog")?.open) form.closest("dialog").close();
          await caricaBersagli(localeId, aggiornaDisponibilita);
          if (document.getElementById(`edge-${localeId}`)) await caricaEdgeDevices(localeId, aggiornaDisponibilita);
          await aggiornaDisponibilita();
        } catch (e) {
          notificaErrore(e);
          salva.disabled = false;
        }
      });
      scheda.querySelector("[data-elimina-bersaglio]").addEventListener("click", async () => {
        menu.open = false;
        if (!await conferma(`Eliminare definitivamente il bersaglio "${bersaglio.identificativoLocale}"?`)) return;
        try {
          await Api.del(`/bersagli/${bersaglio.id}`);
          notifica("Bersaglio eliminato");
          await caricaBersagli(localeId, aggiornaDisponibilita);
          if (document.getElementById(`edge-${localeId}`)) await caricaEdgeDevices(localeId, aggiornaDisponibilita);
          await aggiornaDisponibilita();
        } catch (e) {
          notificaErrore(e);
        }
      });
      select.addEventListener("change", async () => {
        if (bersaglio.stato === "IN_USO" && !await conferma(
          "Conferma che nessuno sta giocando su questo bersaglio. Se esiste una partita in corso, il server impedirà lo sblocco.",
          "Ripristina bersaglio")) {
          select.value = "IN_USO";
          return;
        }
        try {
          await Api.patch(`/bersagli/${select.dataset.bersaglio}/stato`, { stato: select.value });
          notifica("Stato del bersaglio aggiornato");
          await caricaBersagli(localeId, aggiornaDisponibilita);
        } catch (e) {
          notificaErrore(e);
          select.value = bersaglio.stato;
        }
      });
    }
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
  }
}

/**
 * Collega il modulo di registrazione edge ai locali e ai bersagli disponibili.
 *
 * @param {HTMLElement} layout Struttura della pagina di gestione.
 * @param {Array<Object>} locali Locali da consultare.
 * @returns {Function} Funzione che aggiorna la disponibilità nel modulo.
 * 
 */
function configuraCreazioneEdge(layout, locali) {
  const sezione = layout.querySelector("#locale-dispositivi");
  const bottoneCrea = sezione.querySelector("#btn-crea-edge");
  const form = sezione.querySelector("#form-crea-edge");
  const campoLocale = form.elements.namedItem("localeId");
  const campoBersaglio = form.elements.namedItem("dartboardId");
  const avviso = sezione.querySelector("#edge-token-creato");
  const messaggio = sezione.querySelector("#edge-disponibilita");
  let bersagliLiberiPerLocale = new Map();
  let richiestaCorrente = 0;

  /**
   * Aggiorna il selettore dei bersagli per il locale scelto. 
   */
  function aggiornaBersagli() {
    const bersagli = bersagliLiberiPerLocale.get(Number(campoLocale.value)) || [];
    campoBersaglio.replaceChildren(...bersagli.map((b) => new Option(b.identificativoLocale, b.id)));
    campoBersaglio.disabled = !bersagli.length;
  }

  /**
   * Aggiorna i controlli del modulo secondo i dispositivi e i bersagli disponibili. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function aggiornaDisponibilita() {
    const richiesta = ++richiestaCorrente;
    bottoneCrea.disabled = true;
    try {
      const opzioni = await Promise.all(locali.map(async (locale) => {
        const [rispostaDispositivi, rispostaBersagli] = await Promise.all([
          caricaTutteLePagine((page, size) => Api.get("/edge-devices" + queryString({ localeId: locale.id, page, size }))),
          caricaTutteLePagine((page, size) => Api.get(`/locali/${locale.id}/bersagli` + queryString({ page, size }))),
        ]);
        const occupati = new Set(rispostaDispositivi.contenuto.map((d) => d.dartboardId));
        return { locale, bersagli: rispostaBersagli.contenuto.filter((b) => !occupati.has(b.id)) };
      }));
      if (richiesta !== richiestaCorrente) return;
      bersagliLiberiPerLocale = new Map(opzioni.map(({ locale, bersagli }) => [locale.id, bersagli]));
      const localeSelezionato = campoLocale.value;
      campoLocale.replaceChildren(...opzioni.filter(({ bersagli }) => bersagli.length)
        .map(({ locale }) => new Option(locale.nome, locale.id)));
      if ([...campoLocale.options].some((opzione) => opzione.value === localeSelezionato)) campoLocale.value = localeSelezionato;
      aggiornaBersagli();
      bottoneCrea.disabled = !campoLocale.options.length;
      messaggio.hidden = !locali.length || !bottoneCrea.disabled;
      messaggio.textContent = "Non ci sono bersagli liberi. Crea un bersaglio nella sezione Bersagli per registrare un dispositivo.";
      if (bottoneCrea.disabled) {
        form.hidden = true;
        bottoneCrea.setAttribute("aria-expanded", "false");
      }
    } catch (e) {
      if (richiesta !== richiestaCorrente) return;
      messaggio.textContent = `Impossibile caricare i bersagli disponibili: ${e.message}`;
      messaggio.hidden = false;
    }
  }

  campoLocale.addEventListener("change", aggiornaBersagli);
  bottoneCrea.addEventListener("click", () => {
    form.hidden = false;
    bottoneCrea.setAttribute("aria-expanded", "true");
    campoLocale.focus();
  });
  form.querySelector("[data-annulla-edge]").addEventListener("click", () => {
    form.hidden = true;
    form.reset();
    aggiornaBersagli();
    bottoneCrea.setAttribute("aria-expanded", "false");
    bottoneCrea.focus();
  });
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const salva = form.querySelector('[type="submit"]');
    const localeId = Number(campoLocale.value);
    const dartboardId = Number(campoBersaglio.value);
    const serialNumber = form.elements.namedItem("serialNumber").value.trim();
    if (!serialNumber) {
      form.elements.namedItem("serialNumber").focus();
      return;
    }
    salva.disabled = true;
    try {
      const registrazione = await registraEdge({ dartboardId, serialNumber }, {
        avviaEdgeLocale: demoEdgeLocale(),
      });
      avviso.hidden = false;
      if (registrazione.autoProvisioned) {
        avviso.innerHTML = `<strong>Edge di prova registrato e avviato su questo PC.</strong><p>Il token e la configurazione sono stati salvati automaticamente. Lo stato passerà a ONLINE alla ricezione del primo segnale di presenza. <a href="${escapeHtml(registrazione.monitorUrl)}" target="_blank" rel="noopener noreferrer">Apri il monitor dell'edge</a>.</p><button type="button" class="secondario" data-chiudi-token>Chiudi</button>`;
      } else {
        avviso.innerHTML = `<strong>Dispositivo registrato. Copia ora il token: viene mostrato solo una volta.</strong>${registrazione.error ? `<p>${escapeHtml(registrazione.error)}</p>` : ""}<p>Nel file di configurazione dell'edge imposta <code>edge.localeId=${localeId}</code>, <code>edge.dartboardId=${dartboardId}</code> e <code>edge.serialNumber=${escapeHtml(serialNumber)}</code>. Salva il token in un file dedicato, per esempio <code>data/edge-${localeId}-${dartboardId}-token.secret</code>, e imposta <code>edge.pathToken</code> con lo stesso percorso.</p><textarea readonly rows="4" aria-label="Token del dispositivo edge"></textarea><button type="button" class="secondario" data-chiudi-token>Ho salvato il token</button>`;
        avviso.querySelector("textarea").value = registrazione.token;
      }
      avviso.querySelector("[data-chiudi-token]").addEventListener("click", () => {
        avviso.replaceChildren();
        avviso.hidden = true;
      });
      form.hidden = true;
      form.reset();
      bottoneCrea.setAttribute("aria-expanded", "false");
      (avviso.querySelector("textarea") || avviso.querySelector("[data-chiudi-token]")).focus();
      await caricaEdgeDevices(localeId, aggiornaDisponibilita);
      await aggiornaDisponibilita();
    } catch (e) {
      notificaErrore(e);
    } finally {
      salva.disabled = false;
    }
  });

  aggiornaDisponibilita();
  return aggiornaDisponibilita;
}

/**
 * Carica i dispositivi edge del locale e collega le azioni disponibili.
 *
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {Function} [aggiornaDisponibilita] Funzione da chiamare dopo una modifica alla disponibilità.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaEdgeDevices(localeId, aggiornaDisponibilita = () => {}) {
  const zona = document.getElementById(`edge-${localeId}`);
  try {
    const [risposta, rispostaBersagli] = await Promise.all([
      caricaTutteLePagine((page, size) =>
        Api.get("/edge-devices" + queryString({ localeId, page, size }))),
      caricaTutteLePagine((page, size) =>
        Api.get(`/locali/${localeId}/bersagli` + queryString({ page, size }))),
    ]);
    const dispositivi = risposta.contenuto;
    const bersagli = rispostaBersagli.contenuto;
    const nomiBersagli = new Map(bersagli.map((b) => [b.id, b.identificativoLocale]));
    zona.innerHTML = `
      ${dispositivi.length ? `<div class="locale-admin-table-scroll"><table>
        <thead><tr><th>Seriale</th><th>Bersaglio</th><th>Stato</th><th>Ultimo heartbeat</th><th>Tiri in coda</th><th>Azioni</th></tr></thead>
        <tbody>${dispositivi.map((d) => `<tr data-edge-id="${d.id}">
          <td>${escapeHtml(d.serialNumber)}</td>
          <td>${escapeHtml(nomiBersagli.get(d.dartboardId) || `ID ${d.dartboardId}`)}</td>
          <td><span class="badge ${CLASSE_STATO_EDGE[d.stato] || "badge-muted"}">${escapeHtml(d.stato)}</span></td>
          <td>${d.ultimoHeartbeat ? new Date(d.ultimoHeartbeat).toLocaleString() : "In attesa del primo segnale"}</td>
          <td>${d.tiriInCoda}</td>
          <td><div class="admin-row-actions"><button type="button" class="secondario" data-modifica-edge title="Modifica heartbeat, soglia offline, QoS e broker">Modifica</button><button type="button" class="pericolo" data-elimina-edge>Elimina</button></div></td>
        </tr><tr class="locale-admin-edge-edit-row" data-edge-edit="${d.id}" hidden><td colspan="6">
          <form class="riga-form locale-admin-edge-form">
            <p class="locale-admin-edge-edit-help">Seriale e bersaglio richiedono una nuova registrazione.</p>
            <div><label>Heartbeat (secondi)<input name="intervalloHeartbeatS" type="number" min="1" step="1" required value="${d.intervalloHeartbeatS}"></label></div>
            <div><label>Soglia offline (secondi)<input name="sogliaOfflineS" type="number" min="1" step="1" required value="${d.sogliaOfflineS}"></label></div>
            <div><label>QoS<select name="qos">${[0, 1, 2].map((q) => `<option value="${q}" ${q === d.qos ? "selected" : ""}>${q}</option>`).join("")}</select></label></div>
            <div><label>URL broker locale<input name="urlBrokerLocale" value="${escapeHtml(d.urlBrokerLocale || "")}" placeholder="Es. tcp://localhost:1883"></label></div>
            <div class="admin-form-action"><button type="submit">Salva</button></div>
            <div class="admin-form-action"><button type="button" class="secondario" data-annulla-modifica-edge>Annulla</button></div>
          </form>
        </td></tr>`).join("")}</tbody>
      </table></div>` : '<div class="stato-vuoto">Nessun dispositivo edge registrato per questo locale.</div>'}`;

    for (const dispositivo of dispositivi) {
      const riga = zona.querySelector(`[data-edge-id="${dispositivo.id}"]`);
      const modifica = zona.querySelector(`[data-edge-edit="${dispositivo.id}"]`);
      const form = modifica.querySelector("form");
      const dialogo = creaDialogoModifica(form, `Modifica dispositivo: ${dispositivo.serialNumber}`, zona);
      modifica.remove();
      riga.querySelector("[data-modifica-edge]").addEventListener("click", () => {
        form.reset();
        dialogo.showModal();
        form.elements.namedItem("intervalloHeartbeatS").focus();
      });
      form.querySelector("[data-annulla-modifica-edge]").addEventListener("click", () => {
        form.reset();
        dialogo.close();
        riga.querySelector("[data-modifica-edge]").focus();
      });
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const salva = form.querySelector('[type="submit"]');
        const intervalloHeartbeatS = Number(form.elements.namedItem("intervalloHeartbeatS").value);
        const sogliaOfflineS = Number(form.elements.namedItem("sogliaOfflineS").value);
        if (sogliaOfflineS <= intervalloHeartbeatS) {
          notificaErrore(new Error("La soglia offline deve essere maggiore dell’intervallo heartbeat."));
          form.elements.namedItem("sogliaOfflineS").focus();
          return;
        }
        salva.disabled = true;
        try {
          await Api.put(`/edge-devices/${dispositivo.id}/config`, {
            intervalloHeartbeatS, sogliaOfflineS,
            qos: Number(form.elements.namedItem("qos").value),
            urlBrokerLocale: form.elements.namedItem("urlBrokerLocale").value.trim() || null,
          });
          notifica("Configurazione del dispositivo aggiornata");
          if (dialogo.open) dialogo.close();
          await caricaEdgeDevices(localeId, aggiornaDisponibilita);
        } catch (e) {
          notificaErrore(e);
          salva.disabled = false;
        }
      });
      riga.querySelector("[data-elimina-edge]").addEventListener("click", async (evento) => {
        const pulsanteElimina = evento.currentTarget;
        if (!await conferma(`Eliminare il dispositivo "${dispositivo.serialNumber}"? Il suo token sarà revocato e il dispositivo non potrà più sincronizzare i tiri finché non verrà registrato di nuovo.`)) return;
        pulsanteElimina.disabled = true;
        try {
          const demoLocale = location.protocol === "http:" &&
            ["127.0.0.1", "localhost"].includes(location.hostname) && location.port === "8090";
          await Api.del(demoLocale ? `/local-edge/${dispositivo.id}` : `/edge-devices/${dispositivo.id}`);
          notifica("Dispositivo edge eliminato");
          await caricaEdgeDevices(localeId, aggiornaDisponibilita);
          await aggiornaDisponibilita();
        } catch (e) {
          notificaErrore(e);
          pulsanteElimina.disabled = false;
        }
      });
    }
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
  }
}

/**
 * Carica e mostra le partite attive con le azioni disponibili.
 *
 * @param {(number|string)} localeId Identificativo del locale.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaPartiteAttive(localeId) {
  const zona = document.getElementById(`partite-${localeId}`);
  try {
    const risposta = await caricaTutteLePagine((page, size) =>
      Api.get("/partite" + queryString({ localeId, stato: "IN_CORSO", page, size })));
    zona.innerHTML = risposta.contenuto.length
      ? `<div class="locale-admin-table-scroll"><table>
        <thead><tr><th>#</th><th>Bersaglio</th><th>Iniziata</th><th>Modalità</th><th>Azioni</th></tr></thead>
        <tbody>${risposta.contenuto.map((p) => `<tr><td>${p.id}</td><td>${p.dartboardId}</td><td>${new Date(p.dataInizio).toLocaleString()}</td><td>${escapeHtml(p.modalita)}</td><td><a href="#/partite/${p.id}">Apri partita</a></td></tr>`).join("")}</tbody>
      </table></div>`
      : '<div class="stato-vuoto">Nessuna partita attiva</div>';
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
  }
}
