import { Api } from "../api.js";
import { Auth } from "../auth.js";

/**
 * Costruisce la tessera HTML di un indicatore statistico.
 *
 * @param {string} etichetta Etichetta da visualizzare.
 * @param {*} valore Valore da elaborare.
 * @param {number} indice Indice dell’indicatore nella pagina.
 * @returns {string} Tessera con etichetta e valore.
 * 
 */
function tessera(etichetta, valore, indice) {
  return `<div class="stat-tile flex flex-col justify-between">
    <div class="stat-tile-top flex justify-between"><span>${etichetta}</span><span class="stat-index">0${indice}</span></div>
    <strong>${valore}</strong>
  </div>`;
}

/**
 * Prepara la pagina delle statistiche personali e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  container.innerHTML = `<div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> IL TUO PERCORSO</div><h1>Ogni partita conta.</h1><p>I numeri dietro i tuoi lanci, tutti in un unico posto.</p></div><div id="contenuto"><div class="stato-caricamento">Caricamento…</div></div>`;
  const contenuto = container.querySelector("#contenuto");
  try {
    const s = await Api.get(`/statistiche/giocatori/${user.userId}`);
    contenuto.innerHTML = `<div class="stats-grid grid gap-4">
      ${tessera("Partite giocate", s.partiteGiocate, 1)}
      ${tessera("Vittorie", s.vittorie, 2)}
      ${tessera("Percentuale vittorie", s.percentualeVittorie.toFixed(1) + "%", 3)}
      ${tessera("Punteggio medio", s.punteggioMedio.toFixed(1), 4)}
      ${tessera("Miglior tiro", s.migliorTiro, 5)}
    </div>`;
  } catch (e) {
    contenuto.innerHTML =
      e.status === 404
        ? '<div class="stato-vuoto">Non hai ancora completato nessuna partita.</div>'
        : `<div class="stato-errore">${e.message}</div>`;
  }
}
