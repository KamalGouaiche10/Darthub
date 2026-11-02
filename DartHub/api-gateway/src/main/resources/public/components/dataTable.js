
/**
 * Raccoglie tutte le pagine, caricandone al massimo quattro alla volta.
 *
 * @param {Function} caricaPagina Funzione che carica una pagina usando indice e dimensione.
 * @param {number} [dimensionePagina] Numero di elementi richiesti per pagina.
 * @returns {Promise<Object>} Risultato con tutti gli elementi raccolti.
 * 
 */
export async function caricaTutteLePagine(caricaPagina, dimensionePagina = 100) {
  const prima = await caricaPagina(0, dimensionePagina);
  const contenuto = [...prima.contenuto];
  const totalePagine = Number(prima.totalePagine) || 0;

  for (let inizio = 1; inizio < totalePagine; inizio += 4) {
    const indici = Array.from({ length: Math.min(4, totalePagine - inizio) }, (_, i) => inizio + i);
    const pagine = await Promise.all(indici.map((pagina) => caricaPagina(pagina, dimensionePagina)));
    for (const risultato of pagine) {
      contenuto.push(...risultato.contenuto);
    }
  }

  return { ...prima, contenuto, pagina: 0, totalePagine: 1, totaleElementi: contenuto.length };
}

/**
 * Crea una tabella con caricamento, ordinamento e filtro delle righe.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} opzioni Opzioni della funzione.
 * @param {Array<Object>} opzioni.colonne Definizioni delle colonne della tabella.
 * @param {Function} opzioni.caricaPagina Funzione che carica una pagina usando indice e dimensione.
 * @param {number} [opzioni.dimensionePagina] Numero di elementi richiesti per pagina.
 * @param {?Function} [opzioni.filtraRiga] Predicato di ricerca applicato alle righe.
 * @returns {Object} Comandi per ricaricare e filtrare la tabella.
 * 
 */
export function creaDataTable(container, { colonne, caricaPagina, dimensionePagina = 100, filtraRiga = null }) {
  let ultimoRisultato = null;
  let colonnaOrdinamento = null;
  let direzioneAscendente = true;
  let richiestaCorrente = 0;
  let testoFiltro = "";

  /**
   * Filtra e ordina le righe secondo la selezione corrente. @returns {Array<Object>} Righe da visualizzare.
   * 
   */
  function righeOrdinate() {
    if (!ultimoRisultato) {
      return [];
    }
    const contenuto = ultimoRisultato.contenuto.filter((riga) => !testoFiltro || !filtraRiga || filtraRiga(riga, testoFiltro));
    if (!colonnaOrdinamento) {
      return contenuto;
    }
    contenuto.sort((a, b) => {
      const va = a[colonnaOrdinamento];
      const vb = b[colonnaOrdinamento];
      const cmp = typeof va === "number" && typeof vb === "number" ? va - vb : String(va).localeCompare(String(vb));
      return direzioneAscendente ? cmp : -cmp;
    });
    return contenuto;
  }

  /**
   * Ridisegna la tabella e collega l’ordinamento delle colonne. 
   */
  function disegna() {
    const intestazione = colonne
      .map(
        (c) =>
          `<th data-chiave="${c.chiave}" class="${c.chiave === colonnaOrdinamento ? "ordinato" : ""}">${c.etichetta}</th>`,
      )
      .join("");
    const righe = righeOrdinate();
    const corpo = righe.length
      ? righe
          .map(
            (riga) =>
              `<tr>${colonne.map((c) => `<td data-etichetta="${c.etichetta}">${c.render ? c.render(riga[c.chiave], riga) : (riga[c.chiave] ?? "")}</td>`).join("")}</tr>`,
          )
          .join("")
      : `<tr><td colspan="${colonne.length}" class="stato-vuoto">${testoFiltro ? "Nessun risultato" : "Nessun elemento"}</td></tr>`;

    container.innerHTML = `
      <div class="table-scroll"><table>
        <thead><tr>${intestazione}</tr></thead>
        <tbody>${corpo}</tbody>
      </table></div>
      <div class="table-summary">${testoFiltro ? `${righe.length} di ` : ""}${ultimoRisultato.totaleElementi} elementi</div>`;

    container.querySelectorAll("th[data-chiave]").forEach((th) => {
      th.addEventListener("click", () => {
        const chiave = th.dataset.chiave;
        if (colonnaOrdinamento === chiave) {
          direzioneAscendente = !direzioneAscendente;
        } else {
          colonnaOrdinamento = chiave;
          direzioneAscendente = true;
        }
        disegna();
      });
    });
  }

  /**
   * Ricarica i dati della tabella e ignora le risposte superate. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function ricarica() {
    const richiesta = ++richiestaCorrente;
    container.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
    try {
      const risultato = await caricaTutteLePagine(caricaPagina, dimensionePagina);
      if (richiesta !== richiestaCorrente) return;
      ultimoRisultato = risultato;
      disegna();
    } catch (e) {
      if (richiesta !== richiestaCorrente) return;
      container.innerHTML = `<div class="stato-errore">${e && e.message ? e.message : "Errore nel caricamento"}</div>`;
    }
  }

  ricarica();
  return {
    ricarica,
    /**
     * Imposta il testo di ricerca e aggiorna le righe visibili.
     *
     * @param {string} testo Testo da visualizzare o cercare.
     * 
     */
    impostaFiltro(testo) {
      testoFiltro = String(testo || "").trim().normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("it");
      if (ultimoRisultato) disegna();
    },
  };
}
