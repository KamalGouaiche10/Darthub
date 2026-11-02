import { chiediTesto } from "./uiDialog.js";
import { Api } from "./api.js";
import { notificaErrore } from "./notify.js";

const PREFISSO_CHIAVE = "darthub.simulatorUrl.";
const DEFAULT_URL = "http://localhost:7200";
const apertureInCorso = new Map();

/**
 * Costruisce la chiave di archiviazione dell’URL del simulatore del bersaglio.
 *
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @returns {string} Chiave da usare in localStorage.
 * 
 */
function chiave(dartboardId) {
  return `${PREFISSO_CHIAVE}${dartboardId}`;
}

/**
 * Rimuove spazi esterni e barre finali dall’indirizzo del simulatore.
 *
 * @param {string} url Indirizzo del pannello del simulatore.
 * @returns {?string} Indirizzo ripulito, oppure null se vuoto.
 * 
 */
function normalizza(url) {
  const pulito = url.trim().replace(/\/+$/, "");
  return pulito || null;
}

/**
 * Chiede l’indirizzo di un simulatore esterno e lo salva dopo la conferma.
 *
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {string} suggerimento Indirizzo da proporre nel campo.
 * @param {(number|string)} matchId Identificativo della partita.
 * 
 */
function chiediUrl(dartboardId, localeId, suggerimento, matchId) {
  chiediTesto({
    titolo: `Simulatore del bersaglio #${dartboardId}`,
    messaggio: "Indica l'indirizzo della macchina a cui è collegato il sensore virtuale.",
    etichetta: "URL del pannello",
    valore: suggerimento,
    /**
     * Valida l’indirizzo inserito, apre il simulatore esterno e salva l’URL.
     *
     * @param {string} risposta Indirizzo inserito dall’utente.
     * @throws {Error} Se l’indirizzo inserito è vuoto.
     * 
     */
    conferma: (risposta) => {
      const url = normalizza(risposta);
      if (!url) throw new Error("Inserisci un URL valido.");
      apriUrl(url, dartboardId, localeId, matchId);
      localStorage.setItem(chiave(dartboardId), url);
    },
  });
}

/**
 * Valida l’URL del simulatore e aggiunge bersaglio, locale e partita.
 *
 * @param {string} url Indirizzo del pannello del simulatore.
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {(number|string)} matchId Identificativo della partita.
 * @returns {string} URL completo del pannello.
 * @throws {Error} Se l’indirizzo non è valido o non usa HTTP o HTTPS.
 * 
 */
function destinazioneUrl(url, dartboardId, localeId, matchId) {
  let destinazione;
  try {
    destinazione = /^\/simulator\/[1-9]\d*\/$/.test(url)
      ? new URL(url, location.origin) : new URL(url);
  } catch {
    throw new Error("Inserisci un URL completo, per esempio http://localhost:7200.");
  }
  if (!["http:", "https:"].includes(destinazione.protocol)) {
    throw new Error("Usa un indirizzo HTTP o HTTPS.");
  }
  destinazione.searchParams.set("dartboardId", dartboardId);
  if (localeId != null) destinazione.searchParams.set("localeId", localeId);
  else destinazione.searchParams.delete("localeId");
  if (matchId != null) destinazione.searchParams.set("matchId", matchId);
  else destinazione.searchParams.delete("matchId");
  return destinazione.href;
}

/**
 * Apre il pannello del simulatore in una nuova scheda.
 *
 * @param {string} url Indirizzo del pannello del simulatore.
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {(number|string)} matchId Identificativo della partita.
 * 
 */
function apriUrl(url, dartboardId, localeId, matchId) {
  window.open(destinazioneUrl(url, dartboardId, localeId, matchId), "_blank", "noopener");
}

/**
 * Richiede l’avvio o il riuso del simulatore e apre il pannello del bersaglio.
 *
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {(number|string)} matchId Identificativo della partita.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function apriGestito(dartboardId, localeId, matchId) {
  const precedente = apertureInCorso.get(dartboardId);
  if (precedente && !precedente.closed) {
    precedente.focus();
    return;
  }
  const scheda = window.open("about:blank", "_blank");
  if (!scheda) {
    notificaErrore(new Error("Consenti l'apertura delle schede per aprire il simulatore."));
    return;
  }
  scheda.opener = null;
  scheda.document.title = "DartHub — Apertura simulatore";
  scheda.document.body.textContent = "Apertura del simulatore…";
  apertureInCorso.set(dartboardId, scheda);
  try {
    const risultato = await Api.post("/simulators/open", { dartboardId });
    const destinazione = destinazioneUrl(risultato.simulatorUrl, dartboardId, localeId, matchId);
    if (!scheda.closed) scheda.location.replace(destinazione);
  } catch (errore) {
    scheda.close();
    notificaErrore(errore);
  } finally {
    if (apertureInCorso.get(dartboardId) === scheda) apertureInCorso.delete(dartboardId);
  }
}

/**
 * Apre il simulatore gestito del bersaglio, riutilizzando l’apertura in corso.
 *
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {(number|string)} matchId Identificativo della partita.
 * @returns {Promise<void>} Completamento della richiesta di apertura.
 * @see cambiaUrlPannelloSimulatore
 * 
 */
export function apriPannelloSimulatore(dartboardId, localeId, matchId) {
  return apriGestito(dartboardId, localeId, matchId);
}

/**
 * Chiede un nuovo URL per aprire e memorizzare un simulatore esterno.
 *
 * @param {(number|string)} dartboardId Identificativo del bersaglio.
 * @param {(number|string)} localeId Identificativo del locale.
 * @param {(number|string)} matchId Identificativo della partita.
 * 
 */
export function cambiaUrlPannelloSimulatore(dartboardId, localeId, matchId) {
  const attuale = localStorage.getItem(chiave(dartboardId)) || DEFAULT_URL;
  chiediUrl(dartboardId, localeId, attuale, matchId);
}
