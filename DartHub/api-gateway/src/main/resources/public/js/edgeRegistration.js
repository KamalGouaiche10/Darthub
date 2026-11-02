import { Api } from "./api.js";

/**
 * Verifica se la pagina è aperta sulla demo HTTP locale alla porta 8090.
 *
 * @param {Location} [location] Indirizzo corrente del browser.
 * @returns {boolean} True per localhost o 127.0.0.1 sulla porta prevista.
 * 
 */
export function demoEdgeLocale(location = globalThis.location) {
  return location.protocol === "http:" &&
    ["127.0.0.1", "localhost"].includes(location.hostname) && location.port === "8090";
}

/**
 * Registra un edge, usando l’avvio locale soltanto nella demo compatibile.
 *
 * @param {Object} dati Dati da inviare o applicare.
 * @param {Object} [opzioni] Opzioni della funzione.
 * @param {boolean} [opzioni.avviaEdgeLocale] Richiede l’avvio locale quando la pagina usa la demo compatibile.
 * @param {Location} [opzioni.location] Indirizzo corrente del browser.
 * @param {Object} [opzioni.api] Client delle API utilizzato per le richieste.
 * @returns {Promise<*>} Risposta della registrazione del dispositivo.
 * 
 */
export function registraEdge(dati, {
  avviaEdgeLocale = false, location = globalThis.location, api = Api,
} = {}) {
  const avvia = avviaEdgeLocale === true && demoEdgeLocale(location);
  return api.post(avvia ? "/local-edge/register" : "/edge-devices/register", dati);
}
