/**
 * Esclude gli account dei dispositivi dall’elenco degli utenti amministrabili.
 *
 * @param {Object} utente Dati dell’utente da verificare.
 * @returns {boolean} True se l’account non è un dispositivo edge.
 * 
 */
export function eUtenteVisibileAdmin(utente) {
  return utente.role !== "EDGE_DEVICE";
}

export const STATISTICHE_GLOBALI = [
  { chiave: "utentiTotali", titolo: "Utenti attivi", descrizione: "Utenti approvati e abilitati, esclusi gli account dei dispositivi.",
    percorsi: ["/users?approvalStatus=APPROVATO"],
      /**
       * Seleziona gli utenti abilitati e visibili nelle statistiche amministrative.
       *
       * @param {Object} utente Dati dell’utente da verificare.
       * @returns {boolean} True se l’utente soddisfa il filtro.
       * 
       */
      filtro: (utente) => utente.enabled && eUtenteVisibileAdmin(utente) },
  { chiave: "localiTotali", titolo: "Locali approvati", descrizione: "Locali con approvazione confermata.",
    percorsi: ["/locali?stato=APPROVATO"] },
  { chiave: "partiteTotali", titolo: "Partite concluse", descrizione: "Partite completate o sincronizzate.",
    percorsi: ["/partite?stato=COMPLETATA", "/partite?stato=SINCRONIZZATA"] },
  { chiave: "torneiTotali", titolo: "Tornei totali", descrizione: "Tutti i tornei, in qualsiasi stato.",
    percorsi: ["/tornei"] },
];

/**
 * Legge il totale degli elementi richiedendo una sola riga.
 *
 * @param {Object} api Client delle API utilizzato per le richieste.
 * @param {string} percorso Percorso API, con eventuali filtri già presenti.
 * @returns {Promise<number>} Totale comunicato dalle API.
 * 
 */
async function totaleElementi(api, percorso) {
  const risposta = await api.get(`${percorso}${percorso.includes("?") ? "&" : "?"}page=0&size=1`);
  return risposta.totaleElementi;
}

/**
 * Raccoglie gli elementi di tutte le pagine del percorso indicato.
 *
 * @param {Object} api Client delle API utilizzato per le richieste.
 * @param {string} percorso Percorso API, con eventuali filtri già presenti.
 * @returns {Promise<Array<Object>>} Elementi restituiti dalle API.
 * 
 */
async function caricaElementi(api, percorso) {
  const dimensione = 100;
  /**
   * Richiede la pagina indicata mantenendo il filtro della statistica. @param {number} pagina Indice della pagina, a partire da zero.
   * @returns {Promise<Object>} Pagina restituita dalle API.
   * 
   */
  const caricaPagina = (pagina) => api.get(`${percorso}${percorso.includes("?") ? "&" : "?"}page=${pagina}&size=${dimensione}`);
  const prima = await caricaPagina(0);
  const elementi = [...prima.contenuto];

  for (let inizio = 1; inizio < prima.totalePagine; inizio += 4) {
    const pagine = await Promise.all(
      Array.from({ length: Math.min(4, prima.totalePagine - inizio) }, (_, i) => caricaPagina(inizio + i)),
    );
    for (const pagina of pagine) elementi.push(...pagina.contenuto);
  }
  return elementi;
}

/**
 * Carica e filtra gli elementi associati a una statistica globale.
 *
 * @param {Object} api Client delle API utilizzato per le richieste.
 * @param {string} chiave Chiave del dato o della statistica.
 * @returns {Promise<Array<Object>>} Elementi che contribuiscono alla statistica.
 * @throws {Error} Se la chiave della statistica non è riconosciuta.
 * 
 */
export async function caricaElementiStatistica(api, chiave) {
  const statistica = STATISTICHE_GLOBALI.find((s) => s.chiave === chiave);
  if (!statistica) throw new Error("Statistica non disponibile");
  const gruppi = await Promise.all(statistica.percorsi.map((percorso) => caricaElementi(api, percorso)));
  return gruppi.flat().filter(statistica.filtro || (() => true));
}

/**
 * Calcola i totali delle statistiche globali dai dati delle API.
 *
 * @param {Object} api Client delle API utilizzato per le richieste.
 * @returns {Promise<Object>} Totali indicizzati per chiave della statistica.
 * 
 */
export async function calcolaStatisticheGlobali(api) {
  const totali = await Promise.all(STATISTICHE_GLOBALI.map(async (statistica) => {
    const totale = statistica.filtro
      ? (await caricaElementiStatistica(api, statistica.chiave)).length
      : (await Promise.all(statistica.percorsi.map((percorso) => totaleElementi(api, percorso))))
        .reduce((somma, numero) => somma + numero, 0);
    return [statistica.chiave, totale];
  }));
  return Object.fromEntries(totali);
}
