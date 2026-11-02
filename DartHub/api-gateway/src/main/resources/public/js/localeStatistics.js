import { escapeHtmlOrEmpty as testo } from "./html.js";
import { Api } from "./api.js";

/**
 * Carica le statistiche dei locali approvati, distinguendo dati assenti ed errori.
 *
 * @param {Array<Object>} locali Locali da consultare.
 * @param {Object} [api] Client delle API utilizzato per le richieste.
 * @returns {Promise<Array<Object>>} Un risultato per ogni locale approvato.
 * 
 */
export async function caricaStatisticheLocali(locali, api = Api) {
  return Promise.all(locali.filter(l => l.stato === "APPROVATO").map(async locale => {
    try {
      return { locale, dati: await api.get(`/statistiche/locali/${locale.id}`) };
    } catch (e) {
      return e.status === 404 ? { locale, vuoto: true } : { locale, errore: e.message };
    }
  }));
}

/**
 * Mostra le statistiche dei locali e il comando per aggiornarle.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Array<Object>} locali Locali da consultare.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function renderStatisticheLocali(container, locali) {
  container.innerHTML = '<h2>Statistiche dei miei locali</h2><p role="status">Caricamento…</p>';
  const righe = await caricaStatisticheLocali(locali);
  container.innerHTML = `<h2>Statistiche dei miei locali</h2>
    <p>Partite concluse, giocatori distinti e ultimo utilizzo. I dati si aggiornano dopo la ricezione dei risultati.</p>
    <button type="button" class="secondario" id="aggiorna-statistiche-locali">Aggiorna</button>
    <div class="griglia">${righe.length ? righe.map(({ locale, dati, vuoto, errore }) => `
      <div class="card"><h3>${testo(locale.nome)}</h3>
      ${errore ? `<p class="stato-errore" role="alert">${testo(errore)}</p>`
        : vuoto ? '<p class="stato-vuoto">Non sono ancora disponibili risultati per questo locale.</p>'
          : `<dl><dt>Partite concluse</dt><dd>${testo(dati.numeroPartite)}</dd>
            <dt>Giocatori distinti</dt><dd>${testo(dati.giocatoriDistinti)}</dd>
            <dt>Ultimo utilizzo</dt><dd>${dati.ultimoUtilizzo ? testo(new Date(dati.ultimoUtilizzo).toLocaleString("it-IT")) : "Non disponibile"}</dd></dl>`}</div>`).join("")
      : '<p class="stato-vuoto">Nessun locale approvato da consultare.</p>'}</div>`;
  container.querySelector("#aggiorna-statistiche-locali").addEventListener("click", () => renderStatisticheLocali(container, locali));
}
