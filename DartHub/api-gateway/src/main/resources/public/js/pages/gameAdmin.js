import { escapeHtmlOrEmpty as escapeHtml } from "../html.js";

import { Api, queryString } from "../api.js";
import { notifica, notificaErrore } from "../notify.js";
import { conferma } from "../uiDialog.js";
import { creaDialogoModifica } from "../manageDialog.js";
import { caricaTutteLePagine } from "../../components/dataTable.js";
import { renderStandings } from "../../components/standingsTable.js";
import { dataOraLocale, istanteDaCampo, iscrizioniAperte } from "../tournamentDates.js";

const MODALITA_SENSORE = ["EMULATO", "REALE"];
const DIREZIONI_REGOLA = ["DECRESCENTE", "CRESCENTE"];
const CLASSE_STATO_TORNEO = { APERTO: "badge-ok", IN_CORSO: "badge-warn", CHIUSO: "badge-muted" };
const MODALITA_TORNEO = ["INDIVIDUALE", "SQUADRA"];
const FORMULA_TORNEO = ["GIRONE", "ELIMINAZIONE"];
const SEZIONI_GIOCO = [
  { id: "giochi", nome: "Tipi di gioco" },
  { id: "sensori", nome: "Sensori" },
  { id: "regole", nome: "Regole di punteggio" },
  { id: "tornei", nome: "Tornei" },
  { id: "partite", nome: "Partite attive" },
];
/**
 * Prepara la pagina di amministrazione dei giochi e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  container.innerHTML = `
    <div class="admin-layout game-admin-layout">
      <aside class="admin-sidebar" aria-label="Menu amministrazione gioco">
        <div class="admin-sidebar-heading">CONSOLE / GIOCO</div>
        <nav class="admin-nav" aria-label="Sezioni amministrazione gioco">
          ${SEZIONI_GIOCO.map((sezione, indice) => `
            <button type="button" class="admin-nav-link" data-game-section="${sezione.id}" aria-controls="game-${sezione.id}">
              <span class="admin-nav-index">${String(indice + 1).padStart(2, "0")}</span>
              <span>${sezione.nome}</span>
              <span class="admin-nav-arrow" aria-hidden="true">→</span>
            </button>`).join("")}
        </nav>
        <div class="admin-sidebar-foot">DartHub <span>·</span> Game</div>
      </aside>
      <div class="admin-content">
        <header class="admin-header">
          <span class="eyebrow"><span class="eyebrow-dot"></span> CONTROLLO GIOCO</span>
          <h1>Amministrazione gioco</h1>
          <p>Configura giochi, sensori, punteggi e tornei.</p>
        </header>
        <div class="admin-panels">
          <section id="game-giochi" class="admin-panel" hidden>
            <h2>Tipi di gioco</h2>
            <div class="card">
              <h3>Nuovo tipo di gioco</h3>
              <div class="riga-form">
                <div><label for="campo-nome-gioco">Nome</label><input id="campo-nome-gioco" placeholder="Es. Cricket"></div>
                <div><label for="campo-descrizione-gioco">Descrizione</label><input id="campo-descrizione-gioco"></div>
                <div><button type="button" id="btn-crea-gioco">Crea gioco</button></div>
              </div>
            </div>
            <div id="lista-giochi"></div>
          </section>
          <section id="game-sensori" class="admin-panel" hidden>
            <h2>Sensori</h2>
            <p class="game-admin-intro">Definisci i sensori disponibili per ogni tipo di gioco.</p>
            <div id="lista-sensori"></div>
          </section>
          <section id="game-regole" class="admin-panel" hidden>
            <h2>Regole di punteggio</h2>
            <p class="game-admin-intro">Imposta le varianti e il calcolo dei punti per ogni gioco.</p>
            <div id="lista-regole"></div>
          </section>
          <section id="game-tornei" class="admin-panel" hidden>
            <h2>Tornei</h2>
            <div class="card">
              <h3>Nuovo torneo</h3>
              <form id="form-nuovo-torneo" class="riga-form">
                <div><label for="nt-nome">Nome</label><input id="nt-nome" required></div>
                <div><label for="nt-gioco">Gioco</label><select id="nt-gioco" required></select></div>
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
                <div><button type="submit">Crea torneo</button></div>
              </form>
            </div>
            <div id="lista-tornei"></div>
            <dialog id="dettaglio-torneo" class="admin-manage-dialog" aria-label="Gestione torneo"><button type="button" class="admin-dialog-close" aria-label="Chiudi">×</button><div class="admin-dialog-content"></div></dialog>
          </section>
          <section id="game-partite" class="admin-panel" hidden>
            <h2>Partite attive</h2>
            <p>Apri una partita per terminarla esplicitamente senza assegnare una vittoria.</p>
            <div id="game-partite-lista"></div>
          </section>
        </div>
      </div>
    </div>`;

  const layout = container.querySelector(".game-admin-layout");
  const dialogoTorneo = container.querySelector("#dettaglio-torneo");
  dialogoTorneo.querySelector(".admin-dialog-close").addEventListener("click", () => dialogoTorneo.close());
  dialogoTorneo.addEventListener("close", () => {
    dialogoTorneo._richiesta = (dialogoTorneo._richiesta || 0) + 1;
    dialogoTorneo.querySelector(".admin-dialog-content").innerHTML = "";
  });
  /**
   * Mostra la sezione amministrativa selezionata e aggiorna la navigazione. @param {(number|string)} id Identificativo della risorsa.
   * 
   */
  function attivaSezione(id) {
    for (const sezione of SEZIONI_GIOCO) {
      const attiva = sezione.id === id;
      layout.querySelector(`#game-${sezione.id}`).hidden = !attiva;
      const pulsante = layout.querySelector(`[data-game-section="${sezione.id}"]`);
      pulsante.classList.toggle("attivo", attiva);
      if (attiva) pulsante.setAttribute("aria-current", "page");
      else pulsante.removeAttribute("aria-current");
    }
  }
  layout.querySelector(".admin-nav").addEventListener("click", (evento) => {
    const pulsante = evento.target.closest("[data-game-section]");
    if (pulsante) {
      attivaSezione(pulsante.dataset.gameSection);
      if (pulsante.dataset.gameSection === "partite") caricaPartiteAttive();
    }
  });
  attivaSezione("giochi");

  container.querySelector("#btn-crea-gioco").addEventListener("click", async () => {
    const nome = container.querySelector("#campo-nome-gioco");
    const descrizione = container.querySelector("#campo-descrizione-gioco");
    if (!nome.value.trim()) {
      return;
    }
    try {
      await Api.post("/giochi", { nome: nome.value.trim(), descrizione: descrizione.value.trim() || null });
      notifica("Tipo di gioco creato");
      nome.value = "";
      descrizione.value = "";
      await caricaGiochi(container);
      await caricaGiochiInSelect(container.querySelector("#nt-gioco"));
    } catch (e) {
      notificaErrore(e);
    }
  });

  await caricaGiochi(container);
  await caricaGiochiInSelect(container.querySelector("#nt-gioco"));
  gestisciFormNuovoTorneo(container);
  caricaListaTornei(container);
}

/**
 * Carica e mostra le partite attive con le azioni disponibili.
 *
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaPartiteAttive() {
  const zona = document.querySelector("#game-partite-lista");
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
 * Carica i tipi di gioco e prepara le azioni per modificarli.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaGiochi(container) {
  const lista = container.querySelector("#lista-giochi");
  const listaSensori = container.querySelector("#lista-sensori");
  const listaRegole = container.querySelector("#lista-regole");
  try {
    const giochi = await Api.get("/giochi" + queryString({ size: 50 }));
    lista.innerHTML = giochi.contenuto.length
      ? giochi.contenuto
          .map(
            (g) => `
      <div class="card" data-game-id="${g.id}">
        <h2>${escapeHtml(g.nome)}</h2>
        <p style="color: var(--text-muted); margin-top: -0.4rem;">${escapeHtml(g.descrizione)}</p>
        <div class="game-admin-game-actions">
          <button type="button" class="secondario" data-modifica-gioco="${g.id}">Modifica</button>
          <button type="button" class="pericolo" data-elimina-gioco="${g.id}">Elimina</button>
        </div>
        <form class="riga-form game-admin-edit-form" hidden>
          <div><label for="mg-nome-${g.id}">Nome</label><input id="mg-nome-${g.id}" required></div>
          <div><label for="mg-descrizione-${g.id}">Descrizione</label><input id="mg-descrizione-${g.id}"></div>
          <div class="game-admin-edit-actions">
            <button type="submit">Salva</button>
            <button type="button" class="secondario" data-annulla-modifica>Annulla</button>
          </div>
        </form>
        <div class="griglia">
          <div>
            <h3>Sensori</h3>
            <div id="sensori-${g.id}"><div class="stato-caricamento">Caricamento…</div></div>
            <form id="form-sensore-${g.id}" class="riga-form" style="margin-top: 0.6rem;">
              <div><label for="ns-nome-${g.id}">Nome</label><input id="ns-nome-${g.id}" placeholder="Nome sensore" required></div>
              <div><label for="ns-formato-${g.id}">Formato dato</label><input id="ns-formato-${g.id}" placeholder="{campo, campo}" required></div>
              <div><label for="ns-modalita-${g.id}">Modalità</label>
                <select id="ns-modalita-${g.id}">${MODALITA_SENSORE.map((m) => `<option value="${m}">${m}</option>`).join("")}</select>
              </div>
              <div><button type="submit" class="secondario">+ Sensore</button></div>
            </form>
          </div>
          <div>
            <h3>Regole di punteggio</h3>
            <div id="regole-${g.id}"><div class="stato-caricamento">Caricamento…</div></div>
            <form id="form-regola-${g.id}" class="riga-form" style="margin-top: 0.6rem;">
              <div><label for="nr-variante-${g.id}">Variante</label><input id="nr-variante-${g.id}" placeholder="Es. 501" required></div>
              <div><label for="nr-direzione-${g.id}">Direzione</label>
                <select id="nr-direzione-${g.id}">${DIREZIONI_REGOLA.map((d) => `<option value="${d}">${d}</option>`).join("")}</select>
              </div>
              <div><label for="nr-iniziale-${g.id}">Punteggio iniziale</label><input id="nr-iniziale-${g.id}" type="number" value="501"></div>
              <div><label for="nr-obiettivo-${g.id}">Punteggio obiettivo</label><input id="nr-obiettivo-${g.id}" type="number" value="0"></div>
              <div><label for="nr-max-tiri-${g.id}">Tiri max a turno</label><input id="nr-max-tiri-${g.id}" type="number" value="3"></div>
              <div class="game-admin-create-rule-options">
                <label for="nr-double-${g.id}"><input id="nr-double-${g.id}" type="checkbox" checked> Chiusura double</label>
                <label for="nr-bust-${g.id}"><input id="nr-bust-${g.id}" type="checkbox" checked> Bust annulla turno</label>
              </div>
              <div class="game-admin-create-rule-actions"><button type="submit" class="secondario">+ Regola</button></div>
            </form>
          </div>
        </div>
      </div>`,
          )
          .join("")
      : '<div class="stato-vuoto">Nessun tipo di gioco</div>';

    listaSensori.replaceChildren();
    listaRegole.replaceChildren();
    if (!giochi.contenuto.length) {
      listaSensori.innerHTML = '<div class="stato-vuoto">Crea prima un tipo di gioco.</div>';
      listaRegole.innerHTML = '<div class="stato-vuoto">Crea prima un tipo di gioco.</div>';
    }
    lista.querySelectorAll(":scope > .card").forEach((schedaGioco) => {
      const titolo = schedaGioco.querySelector("h2").textContent;
      const griglia = schedaGioco.querySelector(".griglia");
      const [contenutoSensori, contenutoRegole] = [...griglia.children];
      for (const [destinazione, contenuto] of [
        [listaSensori, contenutoSensori],
        [listaRegole, contenutoRegole],
      ]) {
        contenuto.querySelector("h3").remove();
        const scheda = document.createElement("div");
        scheda.className = "card game-admin-config-card";
        const intestazione = document.createElement("h3");
        intestazione.textContent = titolo;
        scheda.append(intestazione, contenuto);
        destinazione.append(scheda);
      }
      griglia.remove();
    });

    for (const g of giochi.contenuto) {
      const scheda = lista.querySelector(`[data-game-id="${g.id}"]`);
      const form = scheda.querySelector(".game-admin-edit-form");
      const dialogo = creaDialogoModifica(form, `Modifica gioco: ${g.nome}`);
      const nome = form.querySelector(`#mg-nome-${g.id}`);
      const descrizione = form.querySelector(`#mg-descrizione-${g.id}`);
      scheda.querySelector("[data-modifica-gioco]").addEventListener("click", () => {
        nome.value = g.nome;
        descrizione.value = g.descrizione || "";
        dialogo.showModal();
        nome.focus();
      });
      form.querySelector("[data-annulla-modifica]").addEventListener("click", () => dialogo.close());
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const salva = form.querySelector('[type="submit"]');
        salva.disabled = true;
        try {
          await Api.put(`/giochi/${g.id}`, {
            nome: nome.value.trim(), descrizione: descrizione.value.trim(),
          });
          notifica("Tipo di gioco aggiornato");
          if (dialogo.open) dialogo.close();
          await caricaGiochi(container);
          await caricaGiochiInSelect(container.querySelector("#nt-gioco"));
        } catch (e) {
          notificaErrore(e);
          salva.disabled = false;
        }
      });
      scheda.querySelector("[data-elimina-gioco]").addEventListener("click", async () => {
        if (!await conferma(`Eliminare definitivamente il gioco "${g.nome}"? Saranno eliminati anche i suoi sensori e le regole di punteggio.`)) return;
        try {
          await Api.del(`/giochi/${g.id}`);
          notifica("Tipo di gioco eliminato");
          await caricaGiochi(container);
          await caricaGiochiInSelect(container.querySelector("#nt-gioco"));
        } catch (e) {
          notificaErrore(e);
        }
      });
    }

    for (const g of giochi.contenuto) {
      caricaSensori(g.id);
      caricaRegole(g.id);
    }

    listaSensori.querySelectorAll("[id^='form-sensore-']").forEach((form) => {
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const gameTypeId = form.id.replace("form-sensore-", "");
        const nome = document.getElementById(`ns-nome-${gameTypeId}`);
        const formato = document.getElementById(`ns-formato-${gameTypeId}`);
        const modalita = document.getElementById(`ns-modalita-${gameTypeId}`);
        try {
          await Api.post(`/giochi/${gameTypeId}/sensori`, {
            nome: nome.value.trim(),
            formatoDato: formato.value.trim(),
            modalita: modalita.value,
          });
          nome.value = "";
          formato.value = "";
          notifica("Sensore creato");
          caricaSensori(gameTypeId);
        } catch (e) {
          notificaErrore(e);
        }
      });
    });

    listaRegole.querySelectorAll("[id^='form-regola-']").forEach((form) => {
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const gameTypeId = form.id.replace("form-regola-", "");
        const variante = document.getElementById(`nr-variante-${gameTypeId}`);
        const direzione = document.getElementById(`nr-direzione-${gameTypeId}`);
        const iniziale = document.getElementById(`nr-iniziale-${gameTypeId}`);
        const obiettivo = document.getElementById(`nr-obiettivo-${gameTypeId}`);
        const maxTiri = document.getElementById(`nr-max-tiri-${gameTypeId}`);
        const chiusuraDouble = document.getElementById(`nr-double-${gameTypeId}`);
        const bustAnnullaTurno = document.getElementById(`nr-bust-${gameTypeId}`);
        try {
          await Api.post(`/giochi/${gameTypeId}/regole`, {
            variante: variante.value.trim(),
            direzione: direzione.value,
            punteggioIniziale: Number(iniziale.value) || 0,
            punteggioObiettivo: Number(obiettivo.value) || 0,
            chiusuraDouble: chiusuraDouble.checked,
            bustAnnullaTurno: bustAnnullaTurno.checked,
            maxTiriPerTurno: Number(maxTiri.value) || 1,
          });
          variante.value = "";
          notifica("Regola creata");
          caricaRegole(gameTypeId);
        } catch (e) {
          notificaErrore(e);
        }
      });
    });
  } catch (e) {
    lista.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    listaSensori.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    listaRegole.innerHTML = `<div class="stato-errore">${e.message}</div>`;
  }
}

/**
 * Carica i sensori del gioco e prepara i relativi moduli di gestione.
 *
 * @param {(number|string)} gameTypeId Identificativo del tipo di gioco.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaSensori(gameTypeId) {
  const zona = document.getElementById(`sensori-${gameTypeId}`);
  try {
    const sensori = await Api.get(`/giochi/${gameTypeId}/sensori` + queryString({ size: 20 }));
    zona.innerHTML = sensori.contenuto.length
      ? sensori.contenuto
          .map((s) => `<div>${s.nome} <span class="badge badge-muted">${s.modalita}</span> <span style="color: var(--text-muted);">${s.formatoDato}</span></div>`)
          .join("")
      : '<div class="stato-vuoto">Nessun sensore</div>';
  } catch (e) {
    zona.innerHTML = `<div class="stato-errore">${e.message}</div>`;
  }
}

/**
 * Carica le regole del gioco e aggiorna i controlli della pagina.
 *
 * @param {(number|string)} gameTypeId Identificativo del tipo di gioco.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaRegole(gameTypeId) {
  const zona = document.getElementById(`regole-${gameTypeId}`);
  try {
    const regole = await caricaTutteLePagine((page, size) =>
      Api.get(`/giochi/${gameTypeId}/regole` + queryString({ page, size })));
    zona.innerHTML = regole.contenuto.length
      ? regole.contenuto
          .map((r) => `<div class="game-admin-rule" data-rule-id="${r.id}">
            <div class="game-admin-rule-header">
              <span>${escapeHtml(r.variante)} — ${r.punteggioIniziale}→${r.punteggioObiettivo} (${escapeHtml(r.direzione.toLowerCase())})</span>
              <div class="game-admin-rule-actions">
                <button type="button" class="secondario" data-modifica-regola>Modifica</button>
                <button type="button" class="pericolo" data-elimina-regola>Elimina</button>
              </div>
            </div>
            <form class="riga-form game-admin-rule-form" hidden>
              <div><label>Variante<input name="variante" required></label></div>
              <div><label>Direzione<select name="direzione">${DIREZIONI_REGOLA.map((d) => `<option value="${d}">${d}</option>`).join("")}</select></label></div>
              <div><label>Punteggio iniziale<input name="punteggioIniziale" type="number" required></label></div>
              <div><label>Punteggio obiettivo<input name="punteggioObiettivo" type="number" required></label></div>
              <div><label>Tiri max a turno<input name="maxTiriPerTurno" type="number" min="1" required></label></div>
              <div class="game-admin-rule-options">
                <label><input name="bustAnnullaTurno" type="checkbox"> Bust annulla turno</label>
                <label><input name="chiusuraDouble" type="checkbox"> Chiusura double</label>
              </div>
              <div class="game-admin-rule-form-actions">
                <button type="submit">Salva</button>
                <button type="button" class="secondario" data-annulla-regola>Annulla</button>
              </div>
            </form>
          </div>`)
          .join("")
      : '<div class="stato-vuoto">Nessuna regola</div>';

    for (const regola of regole.contenuto) {
      const riga = zona.querySelector(`[data-rule-id="${regola.id}"]`);
      const form = riga.querySelector(".game-admin-rule-form");
      const dialogo = creaDialogoModifica(form, `Modifica regola: ${regola.variante}`);
      /**
       * Cerca un controllo del modulo usando il suo nome.
       *
       * @param {string} nome Nome del controllo del modulo.
       * @returns {(Element|RadioNodeList|null)} Controllo o gruppo trovato, oppure null.
       * 
       */
      const campo = (nome) => form.elements.namedItem(nome);
      riga.querySelector("[data-modifica-regola]").addEventListener("click", () => {
        campo("variante").value = regola.variante;
        campo("direzione").value = regola.direzione;
        campo("punteggioIniziale").value = regola.punteggioIniziale;
        campo("punteggioObiettivo").value = regola.punteggioObiettivo;
        campo("maxTiriPerTurno").value = regola.maxTiriPerTurno;
        campo("chiusuraDouble").checked = regola.chiusuraDouble;
        campo("bustAnnullaTurno").checked = regola.bustAnnullaTurno;
        dialogo.showModal();
        campo("variante").focus();
      });
      form.querySelector("[data-annulla-regola]").addEventListener("click", () => dialogo.close());
      form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const salva = form.querySelector('[type="submit"]');
        salva.disabled = true;
        try {
          await Api.put(`/regole/${regola.id}`, {
            variante: campo("variante").value.trim(),
            direzione: campo("direzione").value,
            punteggioIniziale: Number(campo("punteggioIniziale").value),
            punteggioObiettivo: Number(campo("punteggioObiettivo").value),
            maxTiriPerTurno: Number(campo("maxTiriPerTurno").value),
            chiusuraDouble: campo("chiusuraDouble").checked,
            bustAnnullaTurno: campo("bustAnnullaTurno").checked,
          });
          notifica("Regola aggiornata");
          if (dialogo.open) dialogo.close();
          await caricaRegole(gameTypeId);
        } catch (e) {
          notificaErrore(e);
          salva.disabled = false;
        }
      });
      riga.querySelector("[data-elimina-regola]").addEventListener("click", async () => {
        if (!await conferma(`Eliminare definitivamente la regola "${regola.variante}"?`)) return;
        try {
          await Api.del(`/regole/${regola.id}`);
          notifica("Regola eliminata");
          await caricaRegole(gameTypeId);
        } catch (e) {
          notificaErrore(e);
        }
      });
    }
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
    const risposta = await Api.get("/giochi" + queryString({ size: 100 }));
    selectEl.innerHTML = risposta.contenuto.length
      ? risposta.contenuto.map((g) => `<option value="${g.id}">${g.nome}</option>`).join("")
      : "";
  } catch {
    selectEl.innerHTML = "";
  }
}

/**
 * Collega il modulo alla creazione di un torneo e aggiorna l’elenco dopo il salvataggio.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * 
 */
function gestisciFormNuovoTorneo(container) {
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
      caricaListaTornei(container);
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });
}

/**
 * Carica i tornei e collega i comandi per aprirne la gestione.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaListaTornei(container) {
  const lista = container.querySelector("#lista-tornei");
  lista.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let tornei;
  try {
    tornei = await Api.get("/tornei" + queryString({ size: 50 }));
  } catch (e) {
    lista.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    return;
  }

  lista.innerHTML = tornei.contenuto.length
    ? `<div class="game-admin-table-scroll"><table>
      <thead><tr><th>Nome</th><th>Modalità</th><th>Formula</th><th>Inizio</th><th>Fine</th><th>Stato</th><th>Azioni</th></tr></thead>
      <tbody>${tornei.contenuto
        .map(
          (t) => `<tr>
        <td>${t.nome}</td>
        <td>${t.modalita}</td>
        <td>${t.formula}</td>
        <td>${dataOraLocale(t.dataInizio)}</td><td>${dataOraLocale(t.dataFine)}</td>
        <td><span class="badge ${CLASSE_STATO_TORNEO[t.stato] || "badge-muted"}">${t.stato}</span></td>
        <td>
          <div class="game-admin-tournament-actions">
            <button type="button" class="secondario" data-gestisci-torneo="${t.id}">Gestisci</button>
            <button type="button" class="secondario" data-classifica-torneo="${t.id}">Classifica</button>
            <button type="button" class="pericolo" data-elimina-torneo="${t.id}">Elimina</button>
          </div>
        </td>
      </tr>
      <tr id="classifica-riga-${t.id}" style="display: none;"><td colspan="7"><div id="classifica-${t.id}"></div></td></tr>`,
        )
        .join("")}</tbody>
    </table></div>`
    : '<div class="stato-vuoto">Nessun torneo</div>';

  lista.querySelectorAll("[data-gestisci-torneo]").forEach((btn) => {
    btn.addEventListener("click", () => apriGestioneTorneo(container, btn.dataset.gestisciTorneo));
  });
  lista.querySelectorAll("[data-classifica-torneo]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.classificaTorneo;
      const riga = document.getElementById(`classifica-riga-${id}`);
      const visibile = riga.style.display !== "none";
      if (visibile) {
        riga.style.display = "none";
        return;
      }
      riga.style.display = "";
      const zona = document.getElementById(`classifica-${id}`);
      zona.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
      try {
        const classifica = await Api.get(`/tornei/${id}/classifica`);
        renderStandings(zona, classifica);
      } catch (e) {
        zona.innerHTML = `<div class="stato-errore">${e.message}</div>`;
      }
    });
  });
  lista.querySelectorAll("[data-elimina-torneo]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (!await conferma("Eliminare definitivamente questo torneo? L'operazione non è reversibile.")) {
        return;
      }
      try {
        await Api.del(`/tornei/${btn.dataset.eliminaTorneo}`);
        notifica("Torneo eliminato");
        const dialogo = container.querySelector("#dettaglio-torneo");
        if (dialogo.open) dialogo.close();
        caricaListaTornei(container);
      } catch (e) {
        notificaErrore(e);
      }
    });
  });
}

/**
 * Carica il torneo e prepara la finestra di gestione e le relative azioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {(number|string)} id Identificativo della risorsa.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriGestioneTorneo(container, id) {
  const zona = container.querySelector("#dettaglio-torneo");
  if (!zona.open) zona.showModal();
  const richiesta = zona._richiesta = (zona._richiesta || 0) + 1;
  const contenuto = zona.querySelector(".admin-dialog-content");
  contenuto.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  let torneo;
  let iscrizioni;
  try {
    torneo = await Api.get(`/tornei/${id}`);
    iscrizioni = await Api.get(`/tornei/${id}/iscrizioni`);
  } catch (e) {
    if (!zona.open || zona._richiesta !== richiesta) return;
    contenuto.innerHTML = `<div class="stato-errore">${escapeHtml(e.message)}</div>`;
    return;
  }
  if (!zona.open || zona._richiesta !== richiesta) return;

  const etichettaPartecipante = torneo.modalita === "INDIVIDUALE" ? "ID giocatore" : "ID squadra";

  contenuto.innerHTML = `
    <div class="card">
      <h3>Gestisci torneo: ${escapeHtml(torneo.nome)}</h3>
      <p style="color: var(--text-muted); margin-top: -0.4rem;">
        Modalità ${torneo.modalita} · Formula ${torneo.formula} · Stato <span class="badge badge-muted">${torneo.stato}</span>
      </p>
      <p>Inizio: ${dataOraLocale(torneo.dataInizio)} · Fine: ${dataOraLocale(torneo.dataFine)}</p>
      <h4>Locali del torneo</h4>
      <p>Associati: ${torneo.locali.length ? torneo.locali.map((localeId) => `#${localeId}`).join(", ") : "Nessuno. Associa un locale prima dell'inizio."}</p>
      <form id="form-aggiungi-locale-torneo" class="riga-form" ${torneo.stato === "APERTO" ? "" : "hidden"}>
        <div><label for="mt-locale">Locale</label><select id="mt-locale" required></select></div>
        <div><button type="submit">Associa locale</button></div>
      </form>
      <form id="form-modifica-torneo" class="riga-form">
        <div><label for="mt-nome">Nome</label><input id="mt-nome" required></div>
        <div><label for="mt-vittoria">Punti vittoria</label><input id="mt-vittoria" type="number"></div>
        <div><label for="mt-pareggio">Punti pareggio</label><input id="mt-pareggio" type="number"></div>
        <div><label for="mt-sconfitta">Punti sconfitta</label><input id="mt-sconfitta" type="number"></div>
        <div><button type="submit">Salva</button></div>
        <div><button type="button" class="secondario" id="mt-chiudi">Chiudi</button></div>
        ${torneo.stato !== "CHIUSO" ? `<div><button type="button" class="secondario" id="mt-genera-calendario">Genera calendario</button></div>
        <div><button type="button" class="pericolo" id="mt-chiudi-torneo">Chiudi torneo</button></div>` : ""}
      </form>

      <h4>Iscrizioni</h4>
      <div id="mt-iscrizioni"></div>
      ${torneo.modalita === "INDIVIDUALE" ? '<h4>Orari delle partite</h4><div id="mt-partite">Caricamento…</div>'
        : '<p>La presenza sul bersaglio virtuale è disponibile per i tornei individuali.</p>'}
      <form id="form-nuova-iscrizione" class="riga-form" style="margin-top: 0.6rem;" ${iscrizioniAperte(torneo) ? "" : "hidden"}>
        <div><label for="ni-partecipante">${etichettaPartecipante}</label><input id="ni-partecipante" type="number" min="1" required></div>
        <div><button type="submit">+ Iscrivi</button></div>
      </form>
    </div>`;

  zona.querySelector("#mt-nome").value = torneo.nome || "";
  await mostraOrariPartite(zona, id);
  if (!zona.open || zona._richiesta !== richiesta) return;
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
      apriGestioneTorneo(container, id);
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
      caricaListaTornei(container);
      apriGestioneTorneo(container, id);
    } catch (e) {
      notificaErrore(e);
    }
  });

  const bottoneCalendario = zona.querySelector("#mt-genera-calendario");
  if (bottoneCalendario) {
    bottoneCalendario.addEventListener("click", async () => {
      try {
        await Api.post(`/tornei/${id}/calendario`);
        notifica("Calendario generato");
        await mostraOrariPartite(zona, id);
      } catch (e) {
        notificaErrore(e);
      }
    });
  }
  const bottoneChiudiTorneo = zona.querySelector("#mt-chiudi-torneo");
  if (bottoneChiudiTorneo) {
    bottoneChiudiTorneo.addEventListener("click", async () => {
      if (!await conferma("Chiudere definitivamente questo torneo?")) {
        return;
      }
      try {
        await Api.post(`/tornei/${id}/chiudi`);
        notifica("Torneo chiuso");
        caricaListaTornei(container);
        apriGestioneTorneo(container, id);
      } catch (e) {
        notificaErrore(e);
      }
    });
  }

  disegnaIscrizioni(zona, iscrizioni, id, container);

  zona.querySelector("#form-nuova-iscrizione").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const campo = zona.querySelector("#ni-partecipante");
    try {
      await Api.post(`/tornei/${id}/iscrizioni`, { participantId: Number(campo.value) });
      notifica("Iscrizione aggiunta");
      apriGestioneTorneo(container, id);
    } catch (e) {
      notificaErrore(e);
    }
  });
}

/**
 * Mostra gli iscritti al torneo e collega le azioni di gestione.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @param {Array<Object>} iscrizioni Iscrizioni del torneo.
 * @param {(number|string)} tournamentId Identificativo del torneo.
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * 
 */
function disegnaIscrizioni(zona, iscrizioni, tournamentId, container) {
  const area = zona.querySelector("#mt-iscrizioni");
  area.innerHTML = iscrizioni.length
    ? `<table>
        <thead><tr><th>Partecipante</th><th>Tipo</th><th>Iscritto il</th><th></th></tr></thead>
        <tbody>${iscrizioni
          .map(
            (i) => `<tr>
          <td>#${i.participantId}</td>
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
        apriGestioneTorneo(container, tournamentId);
      } catch (e) {
        notificaErrore(e);
      }
    });
  });
}

/**
 * Carica gli abbinamenti del torneo e mostra i controlli degli orari.
 *
 * @param {HTMLElement} zona Area da aggiornare.
 * @param {(number|string)} tournamentId Identificativo del torneo.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function mostraOrariPartite(zona, tournamentId) {
  const area = zona.querySelector("#mt-partite");
  if (!area) return;
  try {
    const matches = await Api.get(`/tornei/${tournamentId}/partite`);
    area.replaceChildren();
    if (!matches.length) { area.textContent = "Il calendario non è ancora stato generato."; return; }
    for (const match of matches) {
      const row = document.createElement("form");
      row.className = "riga-form";
      const label = document.createElement("label");
      label.textContent = `Partita #${match.matchId} · turno ${match.turno} · #${match.homeParticipantId} contro #${match.awayParticipantId} · locale #${match.localeId}`;
      const input = document.createElement("input");
      input.type = "datetime-local"; input.required = true;
      input.setAttribute("aria-label", `Orario partita #${match.matchId}`);
      const duration = document.createElement("input");
      duration.type = "number"; duration.min = "1"; duration.max = "1440"; duration.value = "60";
      duration.setAttribute("aria-label", `Durata in minuti partita #${match.matchId}`);
      try {
        const a = await Api.get(`/tornei/partite/${match.matchId}/appuntamento`);
        const local = new Date(a.scheduledAt);
        input.value = new Date(local.getTime() - local.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        duration.value = a.durationMinutes;
      } catch {   }
      const button = document.createElement("button"); button.textContent = "Salva orario";
      row.append(label, input, duration, button);
      row.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
          await Api.put(`/tornei/${tournamentId}/partite/${match.matchId}/orario`, {
            scheduledAt: new Date(input.value).toISOString(), durationMinutes: Number(duration.value),
          });
          notifica("Orario salvato. Le presenze precedenti sono state azzerate.");
        } catch (e) { notificaErrore(e); }
      });
      area.appendChild(row);
    }
  } catch (e) { area.textContent = e.message; }
}
