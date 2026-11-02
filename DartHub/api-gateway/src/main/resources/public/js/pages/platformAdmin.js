import { escapeHtmlOrEmpty as escapeHtml } from "../html.js";
import { Api, queryString } from "../api.js";
import { notifica, notificaErrore } from "../notify.js";
import { conferma } from "../uiDialog.js";
import { caricaTutteLePagine, creaDataTable } from "../../components/dataTable.js";
import { attivaMostraPassword } from "../passwordVisibility.js";
import { righeApprovazione } from "./approvalQueue.js";
import { dataOraLocale, istanteDaCampo, iscrizioniAperte } from "../tournamentDates.js";
import { calcolaStatisticheGlobali, caricaElementiStatistica, STATISTICHE_GLOBALI, eUtenteVisibileAdmin } from "../platformStatistics.js";

const RUOLI_ASSEGNABILI = ["PLAYER", "LOCALE_ADMIN", "GAME_ADMIN", "PLATFORM_ADMIN"];
const TIPI_LOCALE = ["PUBBLICO", "PRIVATO"];
const STATI_BERSAGLIO = ["DISPONIBILE", "MANUTENZIONE", "OFFLINE"];
const MODALITA_TORNEO = ["INDIVIDUALE", "SQUADRA"];
const FORMULA_TORNEO = ["GIRONE", "ELIMINAZIONE"];
/**
 * Sceglie lo stile del badge in base alla raggiungibilità del servizio.
 *
 * @param {boolean} raggiungibile Indica se il servizio risponde.
 * @returns {string} Classe CSS dello stato.
 * 
 */
const CLASSE_STATO_SERVIZIO = (raggiungibile) => (raggiungibile ? "badge-ok" : "badge-danger");
const SEZIONI_ADMIN = [
  { id: "statistiche", nome: "Statistiche globali" },
  { id: "servizi", nome: "Stato dei servizi" },
  { id: "approvazioni", nome: "Coda di approvazione" },
  { id: "users", nome: "Users" },
  { id: "locali", nome: "Locali" },
  { id: "tornei", nome: "Tornei" },
  { id: "partite", nome: "Partite attive" },
];

let giochiCache = null;
/**
 * Normalizza un testo per confronti senza accenti e differenze di maiuscole.
 *
 * @param {*} valore Valore da elaborare.
 * @returns {string} Testo normalizzato.
 * 
 */
const normalizzaNome = (valore) => String(valore ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("it");
/**
 * Chiude la finestra se è attualmente aperta.
 *
 * @param {HTMLDialogElement} dialogo Finestra da chiudere.
 * 
 */
const chiudiDialogo = (dialogo) => { if (dialogo.open) dialogo.close(); };

/**
 * Carica e mostra le partite attive con le azioni disponibili.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaPartiteAttive(container) {
  const zona = container.querySelector("#platform-partite-lista");
  if (!zona) return;
  try {
    const risposta = await caricaTutteLePagine((page, size) =>
      Api.get("/partite" + queryString({ stato: "IN_CORSO", page, size })));
    zona.innerHTML = risposta.contenuto.length
      ? `<div class="locale-admin-table-scroll"><table><thead><tr><th>#</th><th>Locale</th><th>Bersaglio</th><th>Iniziata</th><th>Azioni</th></tr></thead><tbody>${risposta.contenuto.map((p) =>
          `<tr><td>${p.id}</td><td>${p.localeId}</td><td>${p.dartboardId}</td><td>${new Date(p.dataInizio).toLocaleString()}</td><td><a href="#/partite/${p.id}">Apri partita</a></td></tr>`).join("")}</tbody></table></div>`
      : '<div class="stato-vuoto">Nessuna partita attiva</div>';
  } catch (e) {
    zona.textContent = e.message;
  }
}

/**
 * Prepara la pagina di amministrazione della piattaforma e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  container.innerHTML = `
    <h1>Amministrazione piattaforma</h1>

    <div class="card">
      <h2>Statistiche globali</h2>
      <div id="statistiche-globali"><div class="stato-caricamento">Caricamento…</div></div>
      <dialog id="dettaglio-statistica" class="admin-manage-dialog admin-statistics-dialog" aria-labelledby="titolo-statistica"><button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button><div class="admin-dialog-content"></div></dialog>
    </div>

    <div class="card">
      <h2>Stato dei servizi</h2>
      <div id="system-health"><div class="stato-caricamento">Caricamento…</div></div>
    </div>

    <div class="card">
      <h2>Coda di approvazione</h2>
      <p style="color: var(--text-muted); margin-top: -0.4rem;">
        Account di gestori di locale e richieste per ogni locale in attesa di approvazione.
      </p>
      <div class="admin-list-toolbar"><label for="cerca-approvazioni">Cerca gestore o locale</label><input id="cerca-approvazioni" type="search" placeholder="Nome gestore o locale" autocomplete="off"></div>
      <div id="coda-approvazione"><div class="stato-caricamento">Caricamento…</div></div>
    </div>

    <div class="card">
      <h2>Utenti</h2>
      <h3>Nuovo utente</h3>
      <form id="form-nuovo-utente" class="riga-form">
        <div><label for="nu-username">Nome utente</label><input id="nu-username" required></div>
        <div id="nu-riga-nickname"><label for="nu-nickname">Nickname</label><input id="nu-nickname" autocomplete="nickname" required></div>
        <div><label for="nu-nome">Nome</label><input id="nu-nome" required></div>
        <div><label for="nu-cognome">Cognome</label><input id="nu-cognome" required></div>
        <div><label for="nu-email">Email</label><input id="nu-email" type="email" required></div>
        <div><label for="nu-password">Password</label><input id="nu-password" type="password" required></div>
        <div><label for="nu-ruolo">Ruolo</label>
          <select id="nu-ruolo">${RUOLI_ASSEGNABILI.map((r) => `<option value="${r}">${r}</option>`).join("")}</select>
        </div>
        <div class="admin-form-action"><button type="submit">Crea utente</button></div>
      </form>
      <div class="admin-list-toolbar"><label for="cerca-utenti">Cerca utente</label><input id="cerca-utenti" type="search" placeholder="Nome, username o nickname" autocomplete="off"></div>
      <div id="tabella-utenti" style="margin-top: 1rem;"></div>
      <dialog id="modifica-utente" class="admin-manage-dialog admin-edit-dialog" aria-label="Modifica utente"><button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button><div class="admin-dialog-content"></div></dialog>
    </div>

    <div class="card">
      <h2>Locali</h2>
      <h3>Nuovo locale</h3>
      <form id="form-nuovo-locale" class="riga-form">
        <div><label for="nl-nome">Nome</label><input id="nl-nome" required></div>
        <div><label for="nl-indirizzo">Indirizzo</label><input id="nl-indirizzo"></div>
        <div><label for="nl-tipo">Tipo</label>
          <select id="nl-tipo">${TIPI_LOCALE.map((t) => `<option value="${t}">${t}</option>`).join("")}</select>
        </div>
        <div class="admin-owner-picker">
          <label for="nl-owner">Gestore locale</label>
          <input id="nl-owner" type="search" placeholder="Nome, cognome o username" autocomplete="off" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="nl-owner-suggestions" required>
          <input id="nl-owner-id" type="hidden">
          <div id="nl-owner-suggestions" class="admin-owner-suggestions" role="listbox" hidden></div>
        </div>
        <div class="admin-form-action"><button type="submit">Crea locale</button></div>
      </form>
      <div class="admin-list-toolbar"><label for="cerca-locali">Cerca per nome</label><input id="cerca-locali" type="search" placeholder="Nome locale" autocomplete="off"></div>
      <div id="tabella-locali" style="margin-top: 1rem;"></div>
      <dialog id="dettaglio-locale" class="admin-manage-dialog" aria-label="Gestione locale"><button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button><div class="admin-dialog-content"></div></dialog>
    </div>

    <div class="card">
      <h2>Tornei</h2>
      <h3>Nuovo torneo</h3>
      <form id="form-nuovo-torneo" class="riga-form">
        <div><label for="nt-nome">Nome</label><input id="nt-nome" required></div>
        <div><label for="nt-gioco">Gioco</label><select id="nt-gioco"></select></div>
        <div><label for="nt-modalita">Modalità</label>
          <select id="nt-modalita">${MODALITA_TORNEO.map((m) => `<option value="${m}">${m}</option>`).join("")}</select>
        </div>
        <div><label for="nt-formula">Formula</label>
          <select id="nt-formula">${FORMULA_TORNEO.map((f) => `<option value="${f}">${f}</option>`).join("")}</select>
        </div>
        <div><label for="nt-inizio">Inizio (ora locale)</label><input id="nt-inizio" type="datetime-local" required></div>
        <div><label for="nt-fine">Fine (ora locale)</label><input id="nt-fine" type="datetime-local" required></div>
        <div><label for="nt-vittoria">Punti vittoria</label><input id="nt-vittoria" type="number" value="3"></div>
        <div><label for="nt-pareggio">Punti pareggio</label><input id="nt-pareggio" type="number" value="1"></div>
        <div><label for="nt-sconfitta">Punti sconfitta</label><input id="nt-sconfitta" type="number" value="0"></div>
        <div class="admin-form-action"><button type="submit">Crea torneo</button></div>
      </form>
      <div class="admin-list-toolbar"><label for="cerca-tornei">Cerca per nome</label><input id="cerca-tornei" type="search" placeholder="Nome torneo" autocomplete="off"></div>
      <div id="tabella-tornei" style="margin-top: 1rem;"></div>
      <dialog id="dettaglio-torneo" class="admin-manage-dialog" aria-label="Gestione torneo"><button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button><div class="admin-dialog-content"></div></dialog>
    </div>
    <div class="card">
      <h2>Partite attive</h2>
      <p>Apri una partita per terminarla esplicitamente senza assegnare una vittoria.</p>
      <div id="platform-partite-lista"></div>
    </div>`;

  const titolo = container.querySelector(":scope > h1");
  const schede = [...container.querySelectorAll(":scope > .card")];
  const layout = document.createElement("div");
  layout.className = "admin-layout";
  layout.innerHTML = `
    <aside class="admin-sidebar" aria-label="Menu amministrazione">
      <div class="admin-sidebar-heading">CONSOLE / ADMIN</div>
      <nav class="admin-nav" aria-label="Sezioni amministrazione">
        ${SEZIONI_ADMIN.map((sezione, indice) => `
          <button type="button" class="admin-nav-link" data-admin-section="${sezione.id}" aria-controls="admin-${sezione.id}">
            <span class="admin-nav-index">${String(indice + 1).padStart(2, "0")}</span>
            <span>${sezione.nome}</span>
            <span class="admin-nav-arrow" aria-hidden="true">→</span>
          </button>`).join("")}
      </nav>
      <div class="admin-sidebar-foot">DartHub <span>·</span> Platform</div>
    </aside>
    <div class="admin-content">
      <header class="admin-header">
        <span class="eyebrow"><span class="eyebrow-dot"></span> CONTROLLO PIATTAFORMA</span>
        <p>Gestisci utenti, locali, tornei e servizi da un'unica console.</p>
      </header>
      <div class="admin-panels"></div>
    </div>`;
  layout.querySelector(".admin-header").insertBefore(titolo, layout.querySelector(".admin-header p"));
  const pannelli = layout.querySelector(".admin-panels");
  schede.forEach((scheda, indice) => {
    const sezione = document.createElement("section");
    sezione.id = `admin-${SEZIONI_ADMIN[indice].id}`;
    sezione.className = "admin-panel";
    sezione.hidden = true;
    sezione.append(...scheda.childNodes);
    pannelli.append(sezione);
  });
  container.replaceChildren(layout);
  attivaMostraPassword(layout);
  layout.querySelectorAll(".admin-manage-dialog").forEach((dialogo) => {
    dialogo.querySelector(".admin-dialog-close").addEventListener("click", () => dialogo.close());
    dialogo.addEventListener("close", () => {
      dialogo._richiesta = (dialogo._richiesta || 0) + 1;
      dialogo.querySelector(".admin-dialog-content").innerHTML = "";
    });
  });

  const tabelle = { utenti: null, locali: null, tornei: null };
  const inizializzate = new Set();
  /**
   * Mostra la sezione amministrativa selezionata e aggiorna la navigazione. @param {(number|string)} id Identificativo della risorsa.
   * 
   */
  function attivaSezione(id) {
    for (const sezione of SEZIONI_ADMIN) {
      const attiva = sezione.id === id;
      layout.querySelector(`#admin-${sezione.id}`).hidden = !attiva;
      const pulsante = layout.querySelector(`[data-admin-section="${sezione.id}"]`);
      pulsante.classList.toggle("attivo", attiva);
      if (attiva) pulsante.setAttribute("aria-current", "page");
      else pulsante.removeAttribute("aria-current");
    }
    if (id === "statistiche") caricaStatisticheGlobali(container);
    if (id === "partite") caricaPartiteAttive(container);
    if (inizializzate.has(id)) return;
    inizializzate.add(id);
    if (id === "servizi") caricaSystemHealth(container);
    if (id === "approvazioni") {
      caricaCodaApprovazione(container,
        {
          /**
           * Ricarica la coda delle richieste di approvazione.
           *
           * @returns {Promise<void>} Completamento del caricamento.
           * 
           */
          ricarica: () => tabelle.utenti?.ricarica() },
        {
          /**
           * Ricarica la coda delle richieste di approvazione.
           *
           * @returns {Promise<void>} Completamento del caricamento.
           * 
           */
          ricarica: () => tabelle.locali?.ricarica() });
    }
    if (id === "users") {
      tabelle.utenti = creaTabellaUtenti(container);
      gestisciFormNuovoUtente(container, tabelle.utenti);
    }
    if (id === "locali") {
      tabelle.locali = creaTabellaLocali(container);
      gestisciFormNuovoLocale(container, tabelle.locali);
    }
    if (id === "tornei") {
      caricaGiochiInSelect(container.querySelector("#nt-gioco"));
      tabelle.tornei = creaTabellaTornei(container);
      gestisciFormNuovoTorneo(container, tabelle.tornei);
    }
  }
  layout.querySelector(".admin-nav").addEventListener("click", (evento) => {
    const pulsante = evento.target.closest("[data-admin-section]");
    if (pulsante) attivaSezione(pulsante.dataset.adminSection);
  });
  container.querySelector("#statistiche-globali").addEventListener("click", (evento) => {
    const pulsante = evento.target.closest("[data-statistica]");
    if (pulsante) apriDettaglioStatistica(container, pulsante.dataset.statistica);
  });
  attivaSezione("statistiche");
}

/**
 * Carica gestori e locali in attesa e prepara le azioni di approvazione.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaUtenti Comandi per aggiornare la tabella utenti.
 * @param {Object} tabellaLocali Comandi per aggiornare la tabella locali.
 * 
 */
function caricaCodaApprovazione(container, tabellaUtenti, tabellaLocali) {
  const zona = container.querySelector("#coda-approvazione");
  const ricerca = container.querySelector("#cerca-approvazioni");
  ricerca.addEventListener("input", () => filtraCodaApprovazione(zona, ricerca.value));
  disegnaCodaApprovazione(zona);

  zona.addEventListener("click", async (evento) => {
    const pulsante = evento.target.closest("[data-approva-utente], [data-rifiuta-utente], [data-approva-locale], [data-rifiuta-locale]");
    if (!pulsante) {
      return;
    }
    const stato = pulsante.dataset.approvaUtente || pulsante.dataset.approvaLocale ? "APPROVATO" : "RIFIUTATO";
    const idUtente = pulsante.dataset.approvaUtente || pulsante.dataset.rifiutaUtente;
    const idLocale = pulsante.dataset.approvaLocale || pulsante.dataset.rifiutaLocale;
    pulsante.disabled = true;
    try {
      if (idUtente) await Api.patch(`/users/${idUtente}/approvazione`, { stato });
      if (idLocale) {
        await Api.patch(`/locali/${idLocale}/stato`, { stato });
      }
      notifica(stato === "APPROVATO" ? "Richiesta approvata" : "Richiesta rifiutata");
      disegnaCodaApprovazione(zona);
      tabellaUtenti.ricarica();
      tabellaLocali.ricarica();
    } catch (e) {
      notificaErrore(e);
      disegnaCodaApprovazione(zona);
    } finally {
      pulsante.disabled = false;
    }
  });
}

/**
 * Applica il testo di ricerca alla coda delle richieste di approvazione.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @param {string} testo Testo da visualizzare o cercare.
 * 
 */
function filtraCodaApprovazione(zona, testo) {
  const filtro = normalizzaNome(testo.trim());
  zona.querySelectorAll("tbody tr").forEach((riga) => {
    riga.hidden = !normalizzaNome(`${riga.cells[0].textContent} ${riga.cells[2].textContent}`).includes(filtro);
  });
}

/**
 * Mostra le richieste di approvazione che corrispondono al filtro corrente.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function disegnaCodaApprovazione(zona) {
  zona.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let gestori;
  let locali;
  try {
    const [rispostaGestori, rispostaLocali] = await Promise.all([
      caricaTutteLePagine((page, size) => Api.get("/users" + queryString({ role: "LOCALE_ADMIN", page, size }))),
      caricaTutteLePagine((page, size) => Api.get("/locali" + queryString({ stato: "IN_ATTESA", page, size }))),
    ]);
    gestori = rispostaGestori.contenuto;
    locali = rispostaLocali.contenuto;
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    return;
  }

  const righe = righeApprovazione(gestori, locali);
  zona.innerHTML = righe.length
    ? `<div class="table-scroll admin-approval-table"><table>
      <thead><tr><th>Gestore</th><th>Email</th><th>Locale</th><th>Azioni</th></tr></thead>
      <tbody>${righe
        .map(({ gestore: g, locale }) => {
          const idUtente = g?.approvalStatus === "IN_ATTESA" ? g.id : "";
          return `<tr>
            <td data-etichetta="Gestore">${escapeHtml(g?.username || "Gestore non disponibile")}</td>
            <td data-etichetta="Email">${escapeHtml(g?.email || "")}</td>
            <td data-etichetta="Locale">${locale ? `${escapeHtml(locale.nome)} <span style="color: var(--text-muted);">(${escapeHtml(locale.tipo)})</span>` : '<span class="stato-vuoto">Nessun locale inviato</span>'}</td>
            <td data-etichetta="Azioni"><div class="admin-row-actions">
              <button type="button" class="secondario admin-approve" ${idUtente ? `data-approva-utente="${idUtente}"` : ""} ${locale ? `data-approva-locale="${locale.id}"` : ""}>Approva</button>
              <button type="button" class="pericolo" ${idUtente ? `data-rifiuta-utente="${idUtente}"` : ""} ${locale ? `data-rifiuta-locale="${locale.id}"` : ""}>Rifiuta</button>
            </div></td>
          </tr>`;
        })
        .join("")}</tbody>
    </table></div>`
    : '<div class="stato-vuoto">Nessuna richiesta in attesa</div>';
  filtraCodaApprovazione(zona, zona.parentElement.querySelector("#cerca-approvazioni").value);
}

/**
 * Carica e mostra i contatori delle statistiche della piattaforma.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaStatisticheGlobali(container) {
  const zona = container.querySelector("#statistiche-globali");
  zona.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  try {
    const s = await calcolaStatisticheGlobali(Api);
    zona.innerHTML = `<div class="griglia">
      ${STATISTICHE_GLOBALI.map((statistica) => `<button type="button" class="admin-statistic" data-statistica="${statistica.chiave}" aria-haspopup="dialog" aria-controls="dettaglio-statistica">
        <span class="admin-statistic-label">${statistica.titolo}</span>
        <span class="admin-statistic-value">${s[statistica.chiave]}</span>
        <span class="admin-statistic-link">Visualizza elenco <span aria-hidden="true">→</span></span>
      </button>`).join("")}
    </div>`;
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
  }
}

/**
 * Apre l’elenco filtrabile degli elementi che compongono una statistica.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {string} chiave Chiave del dato o della statistica.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriDettaglioStatistica(container, chiave) {
  const statistica = STATISTICHE_GLOBALI.find((s) => s.chiave === chiave);
  if (!statistica) return;
  const dialogo = container.querySelector("#dettaglio-statistica");
  const contenuto = dialogo.querySelector(".admin-dialog-content");
  const richiesta = dialogo._richiesta = (dialogo._richiesta || 0) + 1;
  contenuto.innerHTML = `<h3 id="titolo-statistica">${statistica.titolo}</h3>
    <p class="admin-statistic-description">${statistica.descrizione}</p>
    <div class="admin-statistic-results" aria-live="polite"><div class="stato-caricamento">Caricamento…</div></div>`;
  if (!dialogo.open) dialogo.showModal();
  const risultati = contenuto.querySelector(".admin-statistic-results");
  let elementi;
  try {
    elementi = await caricaElementiStatistica(Api, chiave);
  } catch (e) {
    if (!dialogo.open || dialogo._richiesta !== richiesta) return;
    risultati.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div><button type="button" class="secondario">Riprova</button>`;
    risultati.querySelector("button").addEventListener("click", () => apriDettaglioStatistica(container, chiave));
    return;
  }
  if (!dialogo.open || dialogo._richiesta !== richiesta) return;
  const contatore = container.querySelector(`[data-statistica="${chiave}"] .admin-statistic-value`);
  if (contatore) contatore.textContent = elementi.length;
  /**
   * Definisce una colonna di testo con i caratteri HTML protetti. @param {string} chiave Chiave del dato o della statistica.
   * @param {string} etichetta Etichetta da visualizzare.
   * @returns {Object} Configurazione della colonna di testo.
   * 
   */
  const testo = (chiave, etichetta) => ({ chiave, etichetta, render: escapeHtml });
  /**
   * Definisce una colonna che formatta le date della tabella. @param {string} chiave Chiave del dato o della statistica.
   * @param {string} etichetta Etichetta da visualizzare.
   * @returns {Object} Configurazione della colonna data.
   * 
   */
  const data = (chiave, etichetta) => ({ chiave, etichetta,
    /**
     * Formatta il valore della colonna per la visualizzazione nella tabella.
     *
     * @param {*} valore Valore da elaborare.
     * @returns {string} Contenuto HTML della cella.
     * 
     */
    render: (valore) => valore ? escapeHtml(dataOraLocale(valore)) : "—" });
  const colonne = {
    utentiTotali: [testo("username", "Nome utente"), testo("nickname", "Nickname"), testo("firstName", "Nome"), testo("lastName", "Cognome"), testo("email", "Email"), testo("role", "Ruolo")],
    localiTotali: [testo("nome", "Locale"), testo("indirizzo", "Indirizzo"), testo("tipo", "Tipo"), testo("stato", "Stato")],
    partiteTotali: [testo("id", "Partita"), testo("localeId", "ID locale"), testo("modalita", "Modalità"), testo("stato", "Stato"), data("dataInizio", "Inizio"), data("dataFine", "Fine")],
    torneiTotali: [testo("nome", "Torneo"), testo("modalita", "Modalità"), testo("stato", "Stato"), data("dataInizio", "Inizio"), data("dataFine", "Fine")],
  }[chiave];
  risultati.innerHTML = `<div class="admin-list-toolbar"><label for="cerca-statistica">Cerca nell'elenco</label><input id="cerca-statistica" type="search" placeholder="Cerca…" autocomplete="off"></div><div class="admin-statistic-table"></div>`;
  const tabella = creaDataTable(risultati.querySelector(".admin-statistic-table"), {
    colonne,
    /**
     * Carica una pagina di elementi per la tabella.
     *
     * @returns {Promise<Object>} Risultato paginato da visualizzare.
     * 
     */
    caricaPagina: async () => ({ contenuto: elementi, pagina: 0, totalePagine: 1, totaleElementi: elementi.length }),
    /**
     * Verifica se la riga corrisponde al testo cercato.
     *
     * @param {Object} riga Dati della riga corrente.
     * @param {string} filtro Testo normalizzato da cercare.
     * @returns {boolean} True se la riga deve essere mostrata.
     * 
     */
    filtraRiga: (riga, filtro) => normalizzaNome(colonne.map((colonna) => riga[colonna.chiave] ?? "").join(" ")).includes(filtro),
  });
  risultati.querySelector("#cerca-statistica").addEventListener("input", (evento) => tabella.impostaFiltro(evento.target.value));
}

/**
 * Legge lo stato dei servizi e aggiorna il pannello di monitoraggio.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaSystemHealth(container) {
  const zona = container.querySelector("#system-health");
  try {
    const stato = await Api.get("/system/health");
    zona.innerHTML = `
      <p>Broker MQTT: <span class="badge ${stato.broker === "CONNESSO" ? "badge-ok" : "badge-danger"}">${stato.broker}</span></p>
      <div class="table-scroll"><table>
        <thead><tr><th>Servizio</th><th>Stato</th><th>Versione</th><th>Uptime</th></tr></thead>
        <tbody>${stato.servizi
          .map(
            (s) => `<tr>
          <td>${s.servizio}</td>
          <td><span class="badge ${CLASSE_STATO_SERVIZIO(s.raggiungibile)}">${s.raggiungibile ? "OK" : "IRRAGGIUNGIBILE"}</span></td>
          <td>${s.dettaglio && s.dettaglio.versione ? s.dettaglio.versione : "-"}</td>
          <td>${s.dettaglio && s.dettaglio.uptimeS != null ? s.dettaglio.uptimeS + " s" : "-"}</td>
        </tr>`,
          )
          .join("")}</tbody>
      </table></div>`;
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${e.message}</div>`;
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
  if (!selectEl) {
    return;
  }
  try {
    if (!giochiCache) {
      const risposta = await Api.get("/giochi" + queryString({ size: 100 }));
      giochiCache = risposta.contenuto;
    }
    selectEl.innerHTML = giochiCache.length
      ? giochiCache.map((g) => `<option value="${g.id}">${g.nome}</option>`).join("")
      : "";
  } catch {
    selectEl.innerHTML = "";
  }
}

/**
 * Collega il modulo alla creazione di un utente e aggiorna la tabella.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaUtenti Comandi per aggiornare la tabella utenti.
 * 
 */
function gestisciFormNuovoUtente(container, tabellaUtenti) {
  const form = container.querySelector("#form-nuovo-utente");
  const campoNickname = form.querySelector("#nu-nickname");
  const campoRuolo = form.querySelector("#nu-ruolo");
  /**
   * Aggiorna il nickname proposto nel modulo del nuovo utente. 
   */
  function aggiornaNickname() {
    const giocatore = campoRuolo.value === "PLAYER";
    form.querySelector("#nu-riga-nickname").hidden = !giocatore;
    campoNickname.required = giocatore;
  }
  campoRuolo.addEventListener("change", aggiornaNickname);
  aggiornaNickname();
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const bottone = form.querySelector('button[type="submit"]');
    bottone.disabled = true;
    try {
      const username = container.querySelector("#nu-username").value.trim();
      const creato = await Api.post("/auth/register", {
        username,
        firstName: container.querySelector("#nu-nome").value.trim(),
        lastName: container.querySelector("#nu-cognome").value.trim(),
        email: container.querySelector("#nu-email").value.trim(),
        password: container.querySelector("#nu-password").value,
        nickname: campoRuolo.value === "PLAYER" ? campoNickname.value.trim() : username,
      });
      const ruolo = container.querySelector("#nu-ruolo").value;
      if (ruolo !== "PLAYER") {
        await Api.patch(`/users/${creato.id}/role`, { role: ruolo });
      }
      notifica("Utente creato");
      form.reset();
      aggiornaNickname();
      tabellaUtenti.ricarica();
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });
}

/**
 * Prepara la tabella degli utenti con filtri e azioni di gestione.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Object} Comandi della tabella utenti.
 * 
 */
function creaTabellaUtenti(container) {
  const zona = container.querySelector("#tabella-utenti");
  zona.classList.add("admin-data-table", "admin-users-table");
  const tabella = creaDataTable(zona, {
    colonne: [
      { chiave: "username", etichetta: "Utente",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @param {Object} riga Dati della riga corrente.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v, riga) =>
        `<strong>${escapeHtml([riga.firstName, riga.lastName].filter(Boolean).join(" ") || v)}</strong><br><small>${escapeHtml(v)}</small>` },
      { chiave: "nickname", etichetta: "Nickname",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @param {Object} riga Dati della riga corrente.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v, riga) => escapeHtml(riga.role === "PLAYER" ? v || "—" : "—") },
      { chiave: "email", etichetta: "Email" },
      {
        chiave: "role",
        etichetta: "Ruolo",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @param {Object} riga Dati della riga corrente.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v, riga) =>
          `<select data-ruolo="${riga.id}" aria-label="Ruolo utente">${RUOLI_ASSEGNABILI.map((r) => `<option value="${r}" ${r === v ? "selected" : ""}>${r}</option>`).join("")}</select>`,
      },
      {
        chiave: "enabled",
        etichetta: "Abilitato",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @param {Object} riga Dati della riga corrente.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v, riga) =>
          `<button type="button" class="secondario admin-status-button" data-abilitato="${riga.id}" data-valore="${!v}">${v ? "Disabilita" : "Abilita"}</button>`,
      },
      {
        chiave: "id",
        etichetta: "Azioni",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) =>
          `<div class="admin-row-actions"><button type="button" class="secondario" data-modifica-utente="${v}">Modifica</button><button type="button" class="pericolo" data-elimina-utente="${v}">Elimina</button></div>`,
      },
    ],
    /**
     * Verifica se la riga corrisponde al testo cercato.
     *
     * @param {Object} riga Dati della riga corrente.
     * @param {string} testo Testo da visualizzare o cercare.
     * @returns {boolean} True se la riga deve essere mostrata.
     * 
     */
    filtraRiga: (riga, testo) => normalizzaNome([riga.username, riga.firstName, riga.lastName, riga.nickname].join(" ")).includes(testo),
    /**
     * Carica una pagina di elementi per la tabella.
     *
     * @param {number} pagina Indice della pagina, a partire da zero.
     * @param {number} dimensione Numero di elementi della pagina.
     * @returns {Promise<Object>} Risultato paginato da visualizzare.
     * 
     */
    caricaPagina: async (pagina, dimensione) => {
      const risposta = await Api.get("/users" + queryString({ page: pagina, size: dimensione }));
      return { ...risposta, contenuto: risposta.contenuto.filter(eUtenteVisibileAdmin) };
    },
    dimensionePagina: 100,
  });
  container.querySelector("#cerca-utenti").addEventListener("input", (evento) => tabella.impostaFiltro(evento.target.value));

  zona.addEventListener("change", async (evento) => {
    if (evento.target.dataset.ruolo) {
      try {
        await Api.patch(`/users/${evento.target.dataset.ruolo}/role`, { role: evento.target.value });
        notifica("Ruolo aggiornato");
      } catch (e) {
        notificaErrore(e);
        tabella.ricarica();
      }
    }
  });
  zona.addEventListener("click", async (evento) => {
    if (evento.target.dataset.abilitato) {
      try {
        await Api.patch(`/users/${evento.target.dataset.abilitato}/enabled`, { enabled: evento.target.dataset.valore === "true" });
        notifica("Stato utente aggiornato");
        tabella.ricarica();
      } catch (e) {
        notificaErrore(e);
      }
      return;
    }
    if (evento.target.dataset.modificaUtente) {
      apriModificaUtente(container, evento.target.dataset.modificaUtente, tabella);
      return;
    }
    if (evento.target.dataset.eliminaUtente) {
      if (!await conferma("Eliminare definitivamente questo utente? L'operazione non è reversibile.")) {
        return;
      }
      try {
        await Api.del(`/users/${evento.target.dataset.eliminaUtente}`);
        notifica("Utente eliminato");
        chiudiDialogo(container.querySelector("#modifica-utente"));
        tabella.ricarica();
      } catch (e) {
        notificaErrore(e);
      }
    }
  });

  return tabella;
}

/**
 * Carica l’utente e collega il modulo per modificarne i dati.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {(number|string)} id Identificativo della risorsa.
 * @param {Object} tabellaUtenti Comandi per aggiornare la tabella utenti.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriModificaUtente(container, id, tabellaUtenti) {
  const zona = container.querySelector("#modifica-utente");
  if (!zona.open) zona.showModal();
  const richiesta = zona._richiesta = (zona._richiesta || 0) + 1;
  const contenuto = zona.querySelector(".admin-dialog-content");
  contenuto.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let dati;
  try {
    dati = await Api.get(`/users/${id}`);
  } catch (e) {
    if (!zona.open || zona._richiesta !== richiesta) return;
    contenuto.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
    return;
  }
  if (!zona.open || zona._richiesta !== richiesta) return;

  contenuto.innerHTML = `
    <div class="card">
      <h3>Modifica utente</h3>
      <form id="form-modifica-utente" class="riga-form">
        <div><label for="mu-nome">Nome</label><input id="mu-nome"></div>
        <div><label for="mu-cognome">Cognome</label><input id="mu-cognome"></div>
        ${dati.role === "PLAYER" ? '<div><label for="mu-nickname">Nickname</label><input id="mu-nickname" autocomplete="nickname" required></div>' : ""}
        <div><label for="mu-email">Email</label><input id="mu-email" type="email" required></div>
        <div class="admin-form-action"><button type="submit">Salva</button></div>
        <div class="admin-form-action"><button type="button" class="secondario" id="mu-annulla">Annulla</button></div>
      </form>
    </div>`;

  zona.querySelector("#mu-email").value = dati.email || "";
  zona.querySelector("#mu-nome").value = dati.firstName || "";
  zona.querySelector("#mu-cognome").value = dati.lastName || "";
  if (dati.role === "PLAYER") zona.querySelector("#mu-nickname").value = dati.nickname || "";
  zona.querySelector("#mu-annulla").addEventListener("click", () => {
    zona.close();
  });
  zona.querySelector("#form-modifica-utente").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    try {
      await Api.put(`/users/${id}`, {
        email: zona.querySelector("#mu-email").value.trim(),
        firstName: zona.querySelector("#mu-nome").value.trim(),
        lastName: zona.querySelector("#mu-cognome").value.trim(),
        nickname: dati.role === "PLAYER" ? zona.querySelector("#mu-nickname").value.trim() : null,
        avatarUrl: null,
      });
      notifica("Utente aggiornato");
      chiudiDialogo(zona);
      tabellaUtenti.ricarica();
    } catch (e) {
      notificaErrore(e);
    }
  });
}

/**
 * Collega il modulo di creazione locale e i suggerimenti per il proprietario.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaLocali Comandi per aggiornare la tabella locali.
 * 
 */
function gestisciFormNuovoLocale(container, tabellaLocali) {
  const form = container.querySelector("#form-nuovo-locale");
  const campoGestore = form.querySelector("#nl-owner");
  const idGestore = form.querySelector("#nl-owner-id");
  const elenco = form.querySelector("#nl-owner-suggestions");
  const picker = form.querySelector(".admin-owner-picker");
  let gestori = [];
  let risultati = [];
  let caricamento = true;
  let erroreCaricamento = false;

  /**
   * Nasconde l’elenco dei proprietari suggeriti. 
   */
  function chiudiSuggerimenti() {
    elenco.hidden = true;
    campoGestore.setAttribute("aria-expanded", "false");
  }

  /**
   * Mostra i proprietari che corrispondono alla ricerca corrente. 
   */
  function disegnaSuggerimenti() {
    const ricerca = normalizzaNome(campoGestore.value.trim());
    if (!picker.contains(document.activeElement) || !ricerca || idGestore.value) {
      chiudiSuggerimenti();
      return;
    }
    risultati = gestori.filter((gestore) => normalizzaNome(
      `${gestore.firstName || ""} ${gestore.lastName || ""} ${gestore.username || ""}`,
    ).includes(ricerca)).slice(0, 8);
    elenco.innerHTML = caricamento
      ? '<div class="admin-owner-message">Caricamento gestori…</div>'
      : erroreCaricamento
        ? '<div class="admin-owner-message">Impossibile caricare i gestori</div>'
        : risultati.length
          ? risultati.map((gestore, indice) => {
            const nome = [gestore.firstName, gestore.lastName].filter(Boolean).join(" ").trim();
            return `<button type="button" role="option" aria-selected="false" data-owner-index="${indice}">${escapeHtml(nome || gestore.username)}${nome ? ` <small>@${escapeHtml(gestore.username)}</small>` : ""}</button>`;
          }).join("")
          : '<div class="admin-owner-message">Nessun gestore trovato</div>';
    elenco.hidden = false;
    campoGestore.setAttribute("aria-expanded", "true");
  }

  caricaTutteLePagine((page, size) => Api.get("/users" + queryString({ role: "LOCALE_ADMIN", page, size })))
    .then((risposta) => { gestori = risposta.contenuto; })
    .catch(() => { erroreCaricamento = true; })
    .finally(() => { caricamento = false; disegnaSuggerimenti(); });

  campoGestore.addEventListener("input", () => {
    idGestore.value = "";
    campoGestore.setCustomValidity("");
    disegnaSuggerimenti();
  });
  campoGestore.addEventListener("focus", disegnaSuggerimenti);
  campoGestore.addEventListener("keydown", (evento) => {
    if (evento.key === "Escape") chiudiSuggerimenti();
    if (evento.key === "ArrowDown" && !elenco.hidden) {
      const primaOpzione = elenco.querySelector("[data-owner-index]");
      if (primaOpzione) { evento.preventDefault(); primaOpzione.focus(); }
    }
  });
  elenco.addEventListener("keydown", (evento) => {
    const opzioni = [...elenco.querySelectorAll("[data-owner-index]")];
    const indice = opzioni.indexOf(document.activeElement);
    if (evento.key === "Escape") { chiudiSuggerimenti(); campoGestore.focus(); }
    if (evento.key === "ArrowDown" && indice < opzioni.length - 1) {
      evento.preventDefault(); opzioni[indice + 1].focus();
    }
    if (evento.key === "ArrowUp") {
      evento.preventDefault();
      if (indice > 0) opzioni[indice - 1].focus();
      else campoGestore.focus();
    }
  });
  elenco.addEventListener("click", (evento) => {
    const opzione = evento.target.closest("[data-owner-index]");
    if (!opzione) return;
    const gestore = risultati[Number(opzione.dataset.ownerIndex)];
    if (!gestore) return;
    const nome = [gestore.firstName, gestore.lastName].filter(Boolean).join(" ").trim();
    campoGestore.value = nome ? `${nome} (@${gestore.username})` : gestore.username;
    idGestore.value = String(gestore.id);
    campoGestore.setCustomValidity("");
    chiudiSuggerimenti();
    campoGestore.focus();
  });
  picker.addEventListener("focusout", (evento) => {
    if (!picker.contains(evento.relatedTarget)) chiudiSuggerimenti();
  });
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    if (!idGestore.value) {
      campoGestore.setCustomValidity("Seleziona un gestore dall'elenco");
      campoGestore.reportValidity();
      return;
    }
    const bottone = form.querySelector("button");
    bottone.disabled = true;
    try {
      await Api.post("/locali", {
        nome: container.querySelector("#nl-nome").value.trim(),
        indirizzo: container.querySelector("#nl-indirizzo").value.trim(),
        tipo: container.querySelector("#nl-tipo").value,
        ownerUserId: Number(idGestore.value),
      });
      notifica("Locale creato");
      form.reset();
      chiudiSuggerimenti();
      tabellaLocali.ricarica();
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });
}

/**
 * Prepara la tabella dei locali e associa i nomi dei proprietari.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Object} Comandi della tabella locali.
 * 
 */
function creaTabellaLocali(container) {
  const zona = container.querySelector("#tabella-locali");
  zona.classList.add("admin-data-table");
  let utentiPerId = new Map();
  const tabella = creaDataTable(zona, {
    colonne: [
      { chiave: "nome", etichetta: "Nome" },
      { chiave: "tipo", etichetta: "Tipo" },
      { chiave: "proprietarioNome", etichetta: "Proprietario",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) => escapeHtml(v) },
      {
        chiave: "id",
        etichetta: "Azioni",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) =>
          `<div class="admin-row-actions"><button type="button" class="secondario" data-gestisci-locale="${v}">Gestisci</button><button type="button" class="pericolo" data-elimina-locale="${v}">Elimina</button></div>`,
      },
    ],
    /**
     * Verifica se la riga corrisponde al testo cercato.
     *
     * @param {Object} riga Dati della riga corrente.
     * @param {string} testo Testo da visualizzare o cercare.
     * @returns {boolean} True se la riga deve essere mostrata.
     * 
     */
    filtraRiga: (riga, testo) => normalizzaNome(riga.nome).includes(testo),
    /**
     * Carica una pagina di elementi per la tabella.
     *
     * @param {number} pagina Indice della pagina, a partire da zero.
     * @param {number} dimensione Numero di elementi della pagina.
     * @returns {Promise<Object>} Risultato paginato da visualizzare.
     * 
     */
    caricaPagina: async (pagina, dimensione) => {
      /**
       * Richiede la pagina corrente dei locali mantenendo la dimensione selezionata.
       *
       * @returns {Promise<Object>} Pagina dei locali restituita dalle API.
       * 
       */
      const caricaLocali = () => Api.get("/locali" + queryString({ page: pagina, size: dimensione }));
      let risposta;
      if (pagina === 0) {
        const [locali, utenti] = await Promise.all([
          caricaLocali(),
          caricaTutteLePagine((page, size) => Api.get("/users" + queryString({ page, size }))),
        ]);
        risposta = locali;
        utentiPerId = new Map(utenti.contenuto.map((utente) => [Number(utente.id), utente]));
      } else {
        risposta = await caricaLocali();
      }
      return {
        ...risposta,
        contenuto: risposta.contenuto.map((locale) => {
          const proprietario = utentiPerId.get(Number(locale.ownerUserId));
          const nome = [proprietario?.firstName, proprietario?.lastName].filter(Boolean).join(" ").trim();
          return { ...locale, proprietarioNome: nome || proprietario?.username || `Utente #${locale.ownerUserId}` };
        }),
      };
    },
    dimensionePagina: 100,
  });
  container.querySelector("#cerca-locali").addEventListener("input", (evento) => tabella.impostaFiltro(evento.target.value));

  zona.addEventListener("click", async (evento) => {
    if (evento.target.dataset.gestisciLocale) {
      apriGestioneLocale(container, evento.target.dataset.gestisciLocale, tabella);
      return;
    }
    if (evento.target.dataset.eliminaLocale) {
      if (!await conferma("Eliminare definitivamente questo locale? L'operazione non è reversibile.")) {
        return;
      }
      try {
        await Api.del(`/locali/${evento.target.dataset.eliminaLocale}`);
        notifica("Locale eliminato");
        chiudiDialogo(container.querySelector("#dettaglio-locale"));
        tabella.ricarica();
      } catch (e) {
        notificaErrore(e);
      }
    }
  });

  return tabella;
}

/**
 * Carica il locale e prepara i controlli per dati e bersagli.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {(number|string)} id Identificativo della risorsa.
 * @param {Object} tabellaLocali Comandi per aggiornare la tabella locali.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriGestioneLocale(container, id, tabellaLocali) {
  const zona = container.querySelector("#dettaglio-locale");
  if (!zona.open) zona.showModal();
  const richiesta = zona._richiesta = (zona._richiesta || 0) + 1;
  const contenuto = zona.querySelector(".admin-dialog-content");
  contenuto.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let locale;
  let bersagli;
  try {
    locale = await Api.get(`/locali/${id}`);
    bersagli = await Api.get(`/locali/${id}/bersagli` + queryString({ size: 50 }));
  } catch (e) {
    if (!zona.open || zona._richiesta !== richiesta) return;
    contenuto.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
    return;
  }
  if (!zona.open || zona._richiesta !== richiesta) return;

  contenuto.innerHTML = `
    <div class="card">
      <h3>Gestisci locale: ${escapeHtml(locale.nome)}</h3>
      <form id="form-modifica-locale" class="riga-form">
        <div><label for="ml-nome">Nome</label><input id="ml-nome" required></div>
        <div><label for="ml-indirizzo">Indirizzo</label><input id="ml-indirizzo"></div>
        <div><label for="ml-tipo">Tipo</label>
          <select id="ml-tipo">${TIPI_LOCALE.map((t) => `<option value="${t}">${t}</option>`).join("")}</select>
        </div>
        <div class="admin-form-action"><button type="submit">Salva</button></div>
        <div class="admin-form-action"><button type="button" class="secondario" id="ml-chiudi">Chiudi</button></div>
      </form>

      <h4>Bersagli</h4>
      <div id="ml-bersagli" class="griglia"></div>
      <form id="form-nuovo-bersaglio" class="riga-form" style="margin-top: 0.6rem;">
        <div><label for="nb-id">Identificativo</label><input id="nb-id" required></div>
        <div><label for="nb-gioco">Gioco</label><select id="nb-gioco"></select></div>
        <div class="admin-form-action"><button type="submit">+ Bersaglio</button></div>
      </form>
    </div>`;

  zona.querySelector("#ml-nome").value = locale.nome || "";
  zona.querySelector("#ml-indirizzo").value = locale.indirizzo || "";
  zona.querySelector("#ml-tipo").value = locale.tipo;
  zona.querySelector("#ml-chiudi").addEventListener("click", () => {
    zona.close();
  });

  zona.querySelector("#form-modifica-locale").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    try {
      await Api.put(`/locali/${id}`, {
        nome: zona.querySelector("#ml-nome").value.trim(),
        indirizzo: zona.querySelector("#ml-indirizzo").value.trim(),
        tipo: zona.querySelector("#ml-tipo").value,
      });
      notifica("Locale aggiornato");
      tabellaLocali.ricarica();
      apriGestioneLocale(container, id, tabellaLocali);
    } catch (e) {
      notificaErrore(e);
    }
  });

  disegnaBersagli(zona, bersagli.contenuto, id, container, tabellaLocali);

  await caricaGiochiInSelect(zona.querySelector("#nb-gioco"));
  if (!zona.open || zona._richiesta !== richiesta) return;
  zona.querySelector("#form-nuovo-bersaglio").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const campoId = zona.querySelector("#nb-id");
    const gioco = zona.querySelector("#nb-gioco");
    try {
      await Api.post(`/locali/${id}/bersagli`, {
        identificativoLocale: campoId.value.trim(),
        gameTypeId: Number(gioco.value),
      });
      notifica("Bersaglio creato");
      apriGestioneLocale(container, id, tabellaLocali);
    } catch (e) {
      notificaErrore(e);
    }
  });
}

/**
 * Mostra i bersagli del locale e collega le azioni di modifica.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @param {Array<Object>} bersagli Bersagli del locale.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaLocali Comandi per aggiornare la tabella locali.
 * 
 */
function disegnaBersagli(zona, bersagli, localeId, container, tabellaLocali) {
  const area = zona.querySelector("#ml-bersagli");
  area.innerHTML = bersagli.length
    ? bersagli
        .map(
          (b) => `
      <div class="card" style="margin-bottom: 0;">
        <strong>${b.identificativoLocale}</strong>
        <div>Edge ${b.statoEdge || "ASSENTE"}</div>
        <div style="margin: 0.4rem 0;">
          <select data-bersaglio-stato="${b.id}">
            ${b.stato === "IN_USO" ? '<option value="IN_USO" selected disabled>IN_USO · partita attiva o da verificare</option>' : ""}
            ${STATI_BERSAGLIO.map((s) => `<option value="${s}" ${s === b.stato ? "selected" : ""}>${s}</option>`).join("")}
          </select>
        </div>
        <button type="button" class="pericolo admin-card-action" data-bersaglio-elimina="${b.id}">Elimina</button>
      </div>`,
        )
        .join("")
    : '<div class="stato-vuoto">Nessun bersaglio</div>';

  area.querySelectorAll("[data-bersaglio-stato]").forEach((select) => {
    select.addEventListener("change", async () => {
      const precedente = bersagli.find((b) => String(b.id) === select.dataset.bersaglioStato)?.stato;
      if (precedente === "IN_USO" && !await conferma(
        "Conferma che nessuno sta giocando su questo bersaglio. Se esiste una partita in corso, il server impedirà lo sblocco.",
        "Ripristina bersaglio")) {
        select.value = precedente;
        return;
      }
      try {
        await Api.patch(`/bersagli/${select.dataset.bersaglioStato}/stato`, { stato: select.value });
        notifica("Stato bersaglio aggiornato");
      } catch (e) {
        notificaErrore(e);
        select.value = precedente;
      }
    });
  });
  area.querySelectorAll("[data-bersaglio-elimina]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (!await conferma("Eliminare questo bersaglio?")) {
        return;
      }
      try {
        await Api.del(`/bersagli/${btn.dataset.bersaglioElimina}`);
        notifica("Bersaglio eliminato");
        apriGestioneLocale(container, localeId, tabellaLocali);
      } catch (e) {
        notificaErrore(e);
      }
    });
  });
}

/**
 * Collega il modulo alla creazione di un torneo e aggiorna l’elenco dopo il salvataggio.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaTornei Comandi per aggiornare la tabella tornei.
 * 
 */
function gestisciFormNuovoTorneo(container, tabellaTornei) {
  const form = container.querySelector("#form-nuovo-torneo");
  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const bottone = form.querySelector("button");
    bottone.disabled = true;
    try {
      await Api.post("/tornei", {
        nome: container.querySelector("#nt-nome").value.trim(),
        gameTypeId: Number(container.querySelector("#nt-gioco").value),
        modalita: container.querySelector("#nt-modalita").value,
        formula: container.querySelector("#nt-formula").value,
        dataInizio: istanteDaCampo(container.querySelector("#nt-inizio").value),
        dataFine: istanteDaCampo(container.querySelector("#nt-fine").value),
        puntiVittoria: Number(container.querySelector("#nt-vittoria").value),
        puntiPareggio: Number(container.querySelector("#nt-pareggio").value),
        puntiSconfitta: Number(container.querySelector("#nt-sconfitta").value),
      });
      notifica("Torneo creato");
      form.reset();
      tabellaTornei.ricarica();
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });
}

/**
 * Prepara la tabella dei tornei con filtri e comandi di gestione.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Object} Comandi della tabella tornei.
 * 
 */
function creaTabellaTornei(container) {
  const zona = container.querySelector("#tabella-tornei");
  zona.classList.add("admin-data-table");
  const tabella = creaDataTable(zona, {
    colonne: [
      { chiave: "nome", etichetta: "Nome" },
      { chiave: "modalita", etichetta: "Modalità" },
      { chiave: "formula", etichetta: "Formula" },
      { chiave: "dataInizio", etichetta: "Inizio", render: dataOraLocale },
      { chiave: "dataFine", etichetta: "Fine", render: dataOraLocale },
      { chiave: "stato", etichetta: "Stato" },
      {
        chiave: "id",
        etichetta: "Azioni",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) =>
          `<div class="admin-row-actions"><button type="button" class="secondario" data-gestisci-torneo="${v}">Gestisci</button><button type="button" class="pericolo" data-elimina-torneo="${v}">Elimina</button></div>`,
      },
    ],
    /**
     * Verifica se la riga corrisponde al testo cercato.
     *
     * @param {Object} riga Dati della riga corrente.
     * @param {string} testo Testo da visualizzare o cercare.
     * @returns {boolean} True se la riga deve essere mostrata.
     * 
     */
    filtraRiga: (riga, testo) => normalizzaNome(riga.nome).includes(testo),
    /**
     * Carica una pagina di elementi per la tabella.
     *
     * @param {number} pagina Indice della pagina, a partire da zero.
     * @param {number} dimensione Numero di elementi della pagina.
     * @returns {Promise<Object>} Risultato paginato da visualizzare.
     * 
     */
    caricaPagina: (pagina, dimensione) => Api.get("/tornei" + queryString({ page: pagina, size: dimensione })),
    dimensionePagina: 100,
  });
  container.querySelector("#cerca-tornei").addEventListener("input", (evento) => tabella.impostaFiltro(evento.target.value));

  zona.addEventListener("click", async (evento) => {
    if (evento.target.dataset.gestisciTorneo) {
      apriGestioneTorneo(container, evento.target.dataset.gestisciTorneo, tabella);
      return;
    }
    if (evento.target.dataset.eliminaTorneo) {
      if (!await conferma("Eliminare definitivamente questo torneo? L'operazione non è reversibile.")) {
        return;
      }
      try {
        await Api.del(`/tornei/${evento.target.dataset.eliminaTorneo}`);
        notifica("Torneo eliminato");
        chiudiDialogo(container.querySelector("#dettaglio-torneo"));
        tabella.ricarica();
      } catch (e) {
        notificaErrore(e);
      }
    }
  });

  return tabella;
}

/**
 * Carica il torneo e prepara la finestra di gestione e le relative azioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {(number|string)} id Identificativo della risorsa.
 * @param {Object} tabellaTornei Comandi per aggiornare la tabella tornei.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriGestioneTorneo(container, id, tabellaTornei) {
  const zona = container.querySelector("#dettaglio-torneo");
  if (!zona.open) zona.showModal();
  const richiesta = zona._richiesta = (zona._richiesta || 0) + 1;
  const contenuto = zona.querySelector(".admin-dialog-content");
  contenuto.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let torneo;
  let iscrizioni;
  let nomiPartecipanti;
  try {
    [torneo, iscrizioni] = await Promise.all([
      Api.get(`/tornei/${id}`), Api.get(`/tornei/${id}/iscrizioni`),
    ]);
    nomiPartecipanti = await caricaNomiPartecipanti(iscrizioni);
  } catch (e) {
    if (!zona.open || zona._richiesta !== richiesta) return;
    contenuto.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
    return;
  }
  if (!zona.open || zona._richiesta !== richiesta) return;

  const etichettaPartecipante = torneo.modalita === "INDIVIDUALE" ? "ID giocatore" : "ID squadra";

  contenuto.innerHTML = `
    <div class="card">
      <div class="admin-tournament-summary">
        <h3>Gestisci torneo: ${escapeHtml(torneo.nome)}</h3>
        <p class="admin-tournament-meta">Modalità ${torneo.modalita} · Formula ${torneo.formula} · Stato <span class="badge badge-muted">${torneo.stato}</span></p>
        <p>Inizio: ${dataOraLocale(torneo.dataInizio)} · Fine: ${dataOraLocale(torneo.dataFine)}</p>
        <h4>Locali del torneo</h4>
        <p>Associati: ${torneo.locali.length ? torneo.locali.map((localeId) => `#${localeId}`).join(", ") : "Nessuno. Associa un locale prima dell'inizio."}</p>
      </div>
      <form id="form-aggiungi-locale-torneo" class="riga-form" ${torneo.stato === "APERTO" ? "" : "hidden"}>
        <div><label for="mt-locale">Locale</label><select id="mt-locale" required></select></div>
        <div><button type="submit">Associa locale</button></div>
      </form>
      <form id="form-modifica-torneo" class="riga-form">
        <div><label for="mt-nome">Nome</label><input id="mt-nome" required></div>
        <div><label for="mt-vittoria">Punti vittoria</label><input id="mt-vittoria" type="number"></div>
        <div><label for="mt-pareggio">Punti pareggio</label><input id="mt-pareggio" type="number"></div>
        <div><label for="mt-sconfitta">Punti sconfitta</label><input id="mt-sconfitta" type="number"></div>
        <div class="admin-form-action"><button type="submit">Salva</button></div>
        <div class="admin-form-action"><button type="button" class="secondario" id="mt-chiudi">Chiudi</button></div>
      </form>

      <h4>Iscrizioni</h4>
      <div id="mt-iscrizioni"></div>
      <form id="form-nuova-iscrizione" class="riga-form" style="margin-top: 0.6rem;" ${iscrizioniAperte(torneo) ? "" : "hidden"}>
        <div><label for="ni-partecipante">${etichettaPartecipante}</label><input id="ni-partecipante" type="number" min="1" required></div>
        <div class="admin-form-action"><button type="submit">+ Iscrivi</button></div>
      </form>
    </div>`;

  zona.querySelector("#mt-nome").value = torneo.nome || "";
  zona.querySelector("#mt-vittoria").value = torneo.puntiVittoria;
  zona.querySelector("#mt-pareggio").value = torneo.puntiPareggio;
  zona.querySelector("#mt-sconfitta").value = torneo.puntiSconfitta;
  const formLocale = zona.querySelector("#form-aggiungi-locale-torneo");
  const selectLocale = zona.querySelector("#mt-locale");
  try {
    const locali = await Api.get("/locali" + queryString({ size: 100 }));
    selectLocale.replaceChildren(...locali.contenuto
      .filter((locale) => !torneo.locali.includes(locale.id))
      .map((locale) => new Option(locale.nome, locale.id)));
    if (!selectLocale.options.length) formLocale.hidden = true;
  } catch {
    formLocale.hidden = true;
  }
  if (!zona.open || zona._richiesta !== richiesta) return;
  formLocale.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    try {
      await Api.post(`/tornei/${id}/locali`, { localeId: Number(selectLocale.value) });
      notifica("Locale associato al torneo");
      apriGestioneTorneo(container, id, tabellaTornei);
    } catch (e) { notificaErrore(e); }
  });
  zona.querySelector("#mt-chiudi").addEventListener("click", () => {
    zona.close();
  });

  zona.querySelector("#form-modifica-torneo").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    try {
      await Api.put(`/tornei/${id}`, {
        nome: zona.querySelector("#mt-nome").value.trim(),
        puntiVittoria: Number(zona.querySelector("#mt-vittoria").value),
        puntiPareggio: Number(zona.querySelector("#mt-pareggio").value),
        puntiSconfitta: Number(zona.querySelector("#mt-sconfitta").value),
      });
      notifica("Torneo aggiornato");
      tabellaTornei.ricarica();
      apriGestioneTorneo(container, id, tabellaTornei);
    } catch (e) {
      notificaErrore(e);
    }
  });

  disegnaIscrizioni(zona, iscrizioni, nomiPartecipanti, id, container, tabellaTornei);

  zona.querySelector("#form-nuova-iscrizione").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const campo = zona.querySelector("#ni-partecipante");
    try {
      await Api.post(`/tornei/${id}/iscrizioni`, { participantId: Number(campo.value) });
      notifica("Iscrizione aggiunta");
      apriGestioneTorneo(container, id, tabellaTornei);
    } catch (e) {
      notificaErrore(e);
    }
  });
}

/**
 * Carica i nomi di giocatori o squadre presenti nelle iscrizioni.
 *
 * @param {Array<Object>} iscrizioni Iscrizioni del torneo.
 * @returns {Promise<Map>} Nomi indicizzati con la chiave del partecipante.
 * 
 */
async function caricaNomiPartecipanti(iscrizioni) {
  const unici = new Map(iscrizioni.map((iscrizione) => [
    `${iscrizione.tipo}:${iscrizione.participantId}`, iscrizione,
  ]));
  const nomi = await Promise.all([...unici].map(async ([chiave, iscrizione]) => {
    try {
      const squadra = iscrizione.tipo === "SQUADRA";
      const partecipante = await Api.get(`/${squadra ? "squadre" : "users"}/${iscrizione.participantId}`);
      return [chiave, squadra ? partecipante.nome : partecipante.nickname || partecipante.username];
    } catch {
      return [chiave, null];
    }
  }));
  return new Map(nomi);
}

/**
 * Mostra gli iscritti al torneo e collega le azioni di gestione.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @param {Array<Object>} iscrizioni Iscrizioni del torneo.
 * @param {Map} nomiPartecipanti Nomi indicizzati per partecipante.
 * @param {(number|string)} tournamentId Identificativo del torneo.
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} tabellaTornei Comandi per aggiornare la tabella tornei.
 * 
 */
function disegnaIscrizioni(zona, iscrizioni, nomiPartecipanti, tournamentId, container, tabellaTornei) {
  const area = zona.querySelector("#mt-iscrizioni");
  area.innerHTML = iscrizioni.length
    ? `<table>
        <thead><tr><th>Partecipante</th><th>Tipo</th><th>Iscritto il</th><th></th></tr></thead>
        <tbody>${iscrizioni
          .map(
            (i) => `<tr>
          <td>${escapeHtml(nomiPartecipanti.get(`${i.tipo}:${i.participantId}`)
            || (i.tipo === "SQUADRA" ? "Squadra non disponibile" : "Utente non disponibile"))}</td>
          <td>${i.tipo}</td>
          <td>${new Date(i.dataIscrizione).toLocaleString()}</td>
          <td><button type="button" class="pericolo" data-iscrizione-rimuovi="${i.participantId}">Rimuovi</button></td>
        </tr>`,
          )
          .join("")}</tbody>
      </table>`
    : '<div class="stato-vuoto">Nessuna iscrizione</div>';

  area.querySelectorAll("[data-iscrizione-rimuovi]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (!await conferma("Rimuovere questa iscrizione?")) {
        return;
      }
      try {
        await Api.del(`/tornei/${tournamentId}/iscrizioni/${btn.dataset.iscrizioneRimuovi}`);
        notifica("Iscrizione rimossa");
        apriGestioneTorneo(container, tournamentId, tabellaTornei);
      } catch (e) {
        notificaErrore(e);
      }
    });
  });
}
