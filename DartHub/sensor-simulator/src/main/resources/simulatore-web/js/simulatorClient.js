
/**
 * Invia una richiesta al simulatore, usando il proxy autenticato quando presente.
 *
 * @param {string} method Metodo HTTP della richiesta.
 * @param {string} path Percorso della risorsa da richiedere.
 * @param {*} [body] Corpo da serializzare in JSON, se presente.
 * @returns {Promise<*>} Corpo JSON della risposta, oppure null se vuoto.
 * @throws {Error} Se la richiesta non riesce.
 * 
 */
async function request(method, path, body) {
  const prefisso = location.pathname?.match(/^\/simulator\/\d+\//)?.[0];
  const headers = { "Content-Type": "application/json" };
  if (prefisso) {
    path = prefisso + path.replace(/^\//, "");
    const token = sessionStorage.getItem("darthub.token");
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  if (method === "POST" && new URLSearchParams(location.search).has("matchId")) {
    path += `?matchId=${encodeURIComponent(new URLSearchParams(location.search).get("matchId"))}`;
  }
  const risposta = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!risposta.ok) {
    let messaggio = risposta.status === 401
      ? "Accedi a DartHub e apri il simulatore dalla pagina della partita."
      : `Errore HTTP ${risposta.status}`;
    try {
      const corpo = await risposta.json();
      if (corpo && corpo.messaggio) {
        messaggio = corpo.messaggio;
      }
    } catch {
    }
    throw new Error(messaggio);
  }

  const testo = await risposta.text();
  return testo ? JSON.parse(testo) : null;
}

export const SimulatorApi = {
  /**
   * Legge lo stato corrente del simulatore. @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  stato: () => request("GET", "/simulatore/stato"),
  /**
   * Avvia la generazione automatica dei tiri. @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  avvia: () => request("POST", "/simulatore/avvia"),
  /**
   * Ferma la generazione automatica dei tiri. @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  ferma: () => request("POST", "/simulatore/ferma"),
  /**
   * Imposta l’intervallo della generazione automatica dei tiri. @param {number} intervalloMs Intervallo tra i tiri automatici in millisecondi.
   * @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  velocita: (intervalloMs) => request("POST", "/simulatore/velocita", { intervalloMs }),
  /**
   * Imposta lo scenario dei tiri simulati. @param {string} scenario Scenario da impostare nel simulatore.
   * @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  scenario: (scenario) => request("POST", "/simulatore/scenario", { scenario }),
  /**
   * Invia al simulatore un tiro con settore e moltiplicatore. @param {string} settore Codice del settore colpito.
   * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
   * @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  tiro: (settore, moltiplicatore) => request("POST", "/simulatore/tiro", { settore, moltiplicatore }),
  /**
   * Richiede una disconnessione temporanea del simulatore dal broker. @param {number} secondi Durata della disconnessione simulata in secondi.
   * @returns {Promise<*>} Risposta JSON del simulatore.
   * 
   */
  disconnetti: (secondi) => request("POST", "/simulatore/disconnetti", { secondi }),
};
