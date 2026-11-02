
const KEY_TOKEN = "darthub.token";
const KEY_REFRESH_TOKEN = "darthub.refreshToken";
const KEY_EXPIRES_AT = "darthub.expiresAt";

/**
 * Rappresenta un errore delle API con stato HTTP e codice applicativo.
 *
 * 
 */
export class ApiError extends Error {
  /**
   * Crea un errore API con stato, codice e messaggio. @param {number} status Stato HTTP, oppure zero per un errore di rete.
   * @param {?string} codice Codice applicativo dell’errore.
   * @param {string} messaggio Messaggio da mostrare all’utente.
   * 
   */
  constructor(status, codice, messaggio) {
    super(messaggio || `Errore HTTP ${status}`);
    this.status = status;
    this.codice = codice;
  }
}

/**
 * Legge il token di accesso della sessione corrente.
 *
 * @returns {?string} Token salvato, oppure null.
 * 
 */
export function getToken() {
  return sessionStorage.getItem(KEY_TOKEN);
}

/**
 * Legge il token usato per rinnovare la sessione.
 *
 * @returns {?string} Token di rinnovo salvato, oppure null.
 * 
 */
export function getRefreshToken() {
  return sessionStorage.getItem(KEY_REFRESH_TOKEN);
}

/**
 * Legge la scadenza del token di accesso.
 *
 * @returns {?number} Istante di scadenza in millisecondi, oppure null.
 * 
 */
export function getExpiresAt() {
  const valore = sessionStorage.getItem(KEY_EXPIRES_AT);
  return valore ? Number(valore) : null;
}

/**
 * Salva i token e calcola la scadenza della sessione.
 *
 * @param {string} accessToken Token di accesso ricevuto dal server.
 * @param {string} refreshToken Token per rinnovare la sessione.
 * @param {number} expiresInSeconds Durata del token di accesso in secondi.
 * @see clearSession
 * 
 */
export function setSession(accessToken, refreshToken, expiresInSeconds) {
  sessionStorage.setItem(KEY_TOKEN, accessToken);
  if (refreshToken) {
    sessionStorage.setItem(KEY_REFRESH_TOKEN, refreshToken);
  }
  sessionStorage.setItem(KEY_EXPIRES_AT, String(Date.now() + expiresInSeconds * 1000));
}

/**
 * Rimuove token e scadenza dalla sessione del browser.
 *
 * 
 */
export function clearSession() {
  sessionStorage.removeItem(KEY_TOKEN);
  sessionStorage.removeItem(KEY_REFRESH_TOKEN);
  sessionStorage.removeItem(KEY_EXPIRES_AT);
}

/**
 * Invia una richiesta JSON autenticata e gestisce gli errori HTTP.
 *
 * @param {string} method Metodo HTTP della richiesta.
 * @param {string} path Percorso della risorsa da richiedere.
 * @param {*} [body] Corpo da serializzare in JSON, se presente.
 * @returns {Promise<*>} Corpo JSON della risposta, oppure null se assente.
 * @throws {ApiError} Se la richiesta non riesce.
 * 
 */
async function request(method, path, body) {
  const headers = { "Content-Type": "application/json" };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "RETE", "Impossibile contattare il server: verifica la connessione");
  }

  let corpo = null;
  const testo = await response.text();
  if (testo) {
    try {
      corpo = JSON.parse(testo);
    } catch {
      corpo = null;
    }
  }

  if (response.status === 401 && !path.startsWith("/auth/")) {
    if (location.hash && location.hash !== "#/login") {
      sessionStorage.setItem("darthub.afterLogin", location.hash);
    }
    clearSession();
    if (location.hash !== "#/login") {
      location.hash = "#/login";
    }
  }

  if (!response.ok) {
    const messaggio = corpo && corpo.messaggio
      ? corpo.messaggio
      : response.status === 403
        ? "Permessi insufficienti per questa operazione"
        : `Errore HTTP ${response.status}`;
    throw new ApiError(response.status, corpo && corpo.codice, messaggio);
  }

  return corpo;
}

export const Api = {
  /**
   * Invia una richiesta GET alle API. @param {string} path Percorso della risorsa da richiedere.
   * @returns {Promise<*>} Corpo della risposta.
   * 
   */
  get: (path) => request("GET", path),
  /**
   * Invia una richiesta POST alle API. @param {string} path Percorso della risorsa da richiedere.
   * @param {*} [body] Corpo da serializzare in JSON, se presente.
   * @returns {Promise<*>} Corpo della risposta.
   * 
   */
  post: (path, body) => request("POST", path, body),
  /**
   * Invia una richiesta PUT alle API. @param {string} path Percorso della risorsa da richiedere.
   * @param {*} [body] Corpo da serializzare in JSON, se presente.
   * @returns {Promise<*>} Corpo della risposta.
   * 
   */
  put: (path, body) => request("PUT", path, body),
  /**
   * Invia una richiesta PATCH alle API. @param {string} path Percorso della risorsa da richiedere.
   * @param {*} [body] Corpo da serializzare in JSON, se presente.
   * @returns {Promise<*>} Corpo della risposta.
   * 
   */
  patch: (path, body) => request("PATCH", path, body),
  /**
   * Invia una richiesta DELETE alle API. @param {string} path Percorso della risorsa da richiedere.
   * @returns {Promise<*>} Corpo della risposta.
   * 
   */
  del: (path) => request("DELETE", path),
};

/**
 * Costruisce la query string escludendo i valori assenti o vuoti.
 *
 * @param {Object} parametri Parametri da inserire nella query string.
 * @returns {string} Query con prefisso ?, oppure stringa vuota.
 * 
 */
export function queryString(parametri) {
  const filtrati = Object.entries(parametri).filter(([, v]) => v !== undefined && v !== null && v !== "");
  if (filtrati.length === 0) {
    return "";
  }
  return "?" + filtrati.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
}
