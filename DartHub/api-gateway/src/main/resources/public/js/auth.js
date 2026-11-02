
import { Api, setSession, clearSession, getToken, getExpiresAt, getRefreshToken } from "./api.js";

let timerRefresh = null;
let profiloCorrente = null;

/**
 * Legge i dati del payload JWT senza verificarne la firma.
 *
 * @param {string} token Token JWT da leggere.
 * @returns {?Object} Payload decodificato, oppure null se non leggibile.
 * 
 */
function decodificaJwt(token) {
  try {
    const payloadBase64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(payloadBase64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/**
 * Programma il rinnovo del token prima della scadenza.
 *
 * @param {number} scadenzaMs Istante di scadenza in millisecondi.
 * 
 */
function programmaRefresh(scadenzaMs) {
  if (timerRefresh) {
    clearTimeout(timerRefresh);
  }
  const attesaMs = Math.max(scadenzaMs - Date.now() - 60_000, 5_000);
  timerRefresh = setTimeout(tentaRefresh, attesaMs);
}

/**
 * Tenta il rinnovo della sessione con il refresh token salvato.
 *
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function tentaRefresh() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return;
  }
  try {
    const risposta = await Api.post("/auth/refresh", { refreshToken });
    setSession(risposta.accessToken, risposta.refreshToken, risposta.expiresInSeconds);
    programmaRefresh(Date.now() + risposta.expiresInSeconds * 1000);
  } catch {
  }
}

export const Auth = {
  /**
   * Autentica l’utente, salva i token e programma il rinnovo. @param {string} username Nome utente dell’account.
   * @param {string} password Password dell’account.
   * @returns {Promise<?Object>} Dati dell’utente ricavati dal token, oppure null.
   * 
   */
  async login(username, password) {
    profiloCorrente = null;
    const risposta = await Api.post("/auth/login", { username, password });
    setSession(risposta.accessToken, risposta.refreshToken, risposta.expiresInSeconds);
    programmaRefresh(Date.now() + risposta.expiresInSeconds * 1000);
    return this.currentUser();
  },

  /**
   * Richiede la revoca del refresh token e cancella la sessione locale. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async logout() {
    const refreshToken = getRefreshToken();
    if (timerRefresh) {
      clearTimeout(timerRefresh);
      timerRefresh = null;
    }
    if (refreshToken) {
      try {
        await Api.post("/auth/logout", { refreshToken });
      } catch {
      }
    }
    clearSession();
    profiloCorrente = null;
  },

  /**
   * Verifica la presenza di un token di accesso nella sessione. @returns {boolean} True se è presente un token.
   * 
   */
  isAuthenticated() {
    return !!getToken();
  },

  /**
   * Ripristina il rinnovo automatico per una sessione non scaduta. 
   */
  riprendiSessione() {
    const scadenzaMs = getExpiresAt();
    if (scadenzaMs && scadenzaMs > Date.now()) {
      programmaRefresh(scadenzaMs);
    }
  },

  /**
   * Ricava dal token l’identità corrente e aggiunge il nickname disponibile. @returns {?Object} Dati dell’utente, oppure null se il token è assente o illeggibile.
   * 
   */
  currentUser() {
    const token = getToken();
    if (!token) {
      return null;
    }
    const claims = decodificaJwt(token);
    if (!claims) {
      return null;
    }
    const userId = Number(claims.sub);
    return { userId, role: claims.role, username: claims.username,
      nickname: profiloCorrente?.id === userId ? profiloCorrente.nickname : null };
  },

  /**
   * Aggiorna il nickname in memoria se il profilo appartiene all’utente corrente. @param {Object} dati Dati da inviare o applicare.
   * 
   */
  aggiornaProfilo(dati) {
    if (dati.id === this.currentUser()?.userId) {
      profiloCorrente = { id: dati.id, nickname: dati.nickname };
    }
  },

  /**
   * Carica il profilo dell’utente corrente e aggiorna il nickname in memoria. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async caricaProfilo() {
    const user = this.currentUser();
    if (!user) return;
    try {
      const dati = await Api.get(`/users/${user.userId}`);
      this.aggiornaProfilo(dati);
    } catch {
    }
  },

  /**
   * Verifica se l’utente corrente possiede uno dei ruoli indicati. @param {...string} ruoli Ruoli ammessi per la verifica.
   * @returns {boolean} True se il ruolo corrente è compreso nell’elenco.
   * 
   */
  hasRole(...ruoli) {
    const user = this.currentUser();
    return !!user && ruoli.includes(user.role);
  },
};
