import { Api, queryString } from "../api.js";

const CLASSE_STATO = { DISPONIBILE: "badge-ok", IN_USO: "badge-warn", MANUTENZIONE: "badge-danger", OFFLINE: "badge-muted" };

/**
 * Costruisce il badge HTML per lo stato del bersaglio.
 *
 * @param {string} stato Stato dell’entità da visualizzare.
 * @returns {string} Badge dello stato.
 * 
 */
function badgeStato(stato) {
  return `<span class="badge ${CLASSE_STATO[stato] || "badge-muted"}">${stato}</span>`;
}

/**
 * Prepara la pagina dei bersagli e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  container.innerHTML = `
    <section class="dashboard-hero grid gap-6">
      <div>
        <div class="eyebrow"><span class="eyebrow-dot"></span> DOVE SI GIOCA</div>
        <h1>Trova il tuo<br><em>prossimo centro.</em></h1>
        <p>Esplora i locali e scopri quali bersagli sono pronti per la tua prossima partita.</p>
        <a href="#/nuova-partita" class="hero-action inline-flex items-center">Organizza una partita <span aria-hidden="true">↗</span></a>
      </div>
      <div class="hero-visual" aria-hidden="true"><div class="hero-ring"><div></div></div><span>PLAY<br>YOUR<br>GAME<span class="hero-dot">.</span></span></div>
    </section>
    <div class="section-heading flex items-end justify-between"><div><span class="eyebrow">LA RETE DARTHUB</span><h2>Postazioni di gioco</h2></div><span id="numero-locali" class="section-count"></span></div>
    <div id="lista-locali" class="venue-list grid gap-4"></div>`;
  const listaLocali = container.querySelector("#lista-locali");

  let locali;
  try {
    locali = await Api.get("/locali" + queryString({ size: 100 }));
  } catch (e) {
    listaLocali.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    return;
  }
  if (!locali.contenuto.length) {
    listaLocali.innerHTML = '<div class="stato-vuoto empty-panel">Nessun locale registrato. Torna a trovarci presto.</div>';
    return;
  }

  container.querySelector("#numero-locali").textContent = `${locali.contenuto.length} ${locali.contenuto.length === 1 ? "locale" : "locali"}`;

  listaLocali.innerHTML = locali.contenuto
    .map(
      (locale) => `
      <article class="card venue-card transition-colors duration-200 hover:border-lime/40">
        <div class="venue-head flex items-start justify-between"><div class="venue-symbol" aria-hidden="true">↗</div><span class="badge badge-muted">${locale.tipo}</span></div>
        <h3>${locale.nome}</h3>
        <p class="venue-address"><span aria-hidden="true">⌖</span> ${locale.indirizzo || "Indirizzo non disponibile"}</p>
        <div class="venue-divider"></div>
        <div class="venue-label">BERSAGLI DEL LOCALE</div>
        <div class="griglia board-grid" id="bersagli-${locale.id}"><div class="stato-caricamento">Caricamento bersagli…</div></div>
      </article>`,
    )
    .join("");

  await Promise.all(locali.contenuto.map((locale) => caricaBersagli(locale.id)));
}

/**
 * Carica i bersagli del locale e aggiorna le relative opzioni o schede.
 *
 * @param {(number|string)} localeId Identificativo del locale.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function caricaBersagli(localeId) {
  const contenitore = document.getElementById(`bersagli-${localeId}`);
  try {
    const bersagli = await Api.get(`/locali/${localeId}/bersagli` + queryString({ size: 50 }));
    contenitore.innerHTML = bersagli.contenuto.length
      ? bersagli.contenuto
          .map(
            (b) => `
        <div class="board-item flex items-center gap-3">
          <div class="board-icon" aria-hidden="true">◎</div>
          <div><strong>${b.identificativoLocale}</strong><small>Bersaglio</small></div>
          ${badgeStato(b.prenotabile ? "DISPONIBILE" : b.stato !== "DISPONIBILE" ? b.stato : b.statoEdge === "ASSENTE" ? "EDGE ASSENTE" : "EDGE OFFLINE")}
        </div>`,
          )
          .join("")
      : '<div class="stato-vuoto">Nessun bersaglio in questo locale</div>';
  } catch (e) {
    contenitore.innerHTML = `<div class="stato-errore">${e.message}</div>`;
  }
}
