
import { Auth } from "./auth.js";
import { Api } from "./api.js";
import { notifica } from "./notify.js";
import { abbinamentoConcluso } from "./tournamentDates.js";

const avvisiVisti = new Map();
const DESTINAZIONE_DOPO_LOGIN = "darthub.afterLogin";
/**
 * Controlla gli abbinamenti dell’utente e notifica le variazioni non ancora mostrate.
 *
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function controllaAvvisiTorneo() {
  if (Auth.currentUser()?.role !== "PLAYER") { avvisiVisti.clear(); return; }
  try {
    const abbinamenti = await Api.get("/tornei/miei-abbinamenti");
    for (const a of abbinamenti) {
      if (a.letto || a.riposo || abbinamentoConcluso(a)) continue;
      const firma = `${a.homeAccepted}:${a.awayAccepted}:${a.scheduledAt}:${a.outcome}`;
      if (avvisiVisti.get(a.matchId) === firma) continue;
      avvisiVisti.set(a.matchId, firma);
      if (a.scheduledAt) notifica(`${a.torneo}: partita contro ${a.avversario} alle ${new Date(a.scheduledAt).toLocaleString()}.`);
      else if (a.homeAccepted || a.awayAccepted) notifica(`${a.torneo}: accettazione registrata per la sfida contro ${a.avversario}.`);
      else notifica(`${a.torneo}: nuova sfida contro ${a.avversario}.`);
    }
  } catch {   }
}

const ROUTES = [
  { pattern: "#/login", pubblica: true,
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/login.js") },
  { pattern: "#/registrazione", pubblica: true,
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/register.js") },
  {
    pattern: "#/profilo",
    ruoli: ["PLAYER", "LOCALE_ADMIN", "GAME_ADMIN", "PLATFORM_ADMIN"],
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/profilo.js"),
  },
  { pattern: "#/bersagli", ruoli: ["PLAYER"], label: "Bersagli",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/dartboards.js") },
  { pattern: "#/nuova-partita", ruoli: ["PLAYER"], label: "Nuova partita",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/matchSetup.js") },
  { pattern: "#/partite/:id", ruoli: ["PLAYER"],
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/matchLive.js") },
  { pattern: "#/tornei/partite/:id/bersaglio", ruoli: ["PLAYER"],
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/tournamentTarget.js") },
  { pattern: "#/storico", ruoli: ["PLAYER"], label: "Storico partite",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/matchHistory.js") },
  { pattern: "#/statistiche", ruoli: ["PLAYER"], label: "Le mie statistiche",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/myStatistics.js") },
  { pattern: "#/tornei", ruoli: ["PLAYER"], label: "Tornei",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/tournaments.js") },
  { pattern: "#/admin-locale", ruoli: ["LOCALE_ADMIN"], label: "I miei locali",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/localeAdmin.js") },
  { pattern: "#/admin-gioco", ruoli: ["GAME_ADMIN"], label: "Gioco",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/gameAdmin.js") },
  { pattern: "#/admin-piattaforma", ruoli: ["PLATFORM_ADMIN"], label: "Piattaforma",
    /**
     * Carica il modulo della pagina associata alla rotta.
     *
     * @returns {Promise<Object>} Modulo JavaScript della pagina.
     * 
     */
    load: () => import("./pages/platformAdmin.js") },
];

const LANDING_PER_RUOLO = {
  PLAYER: "#/bersagli",
  LOCALE_ADMIN: "#/admin-locale",
  GAME_ADMIN: "#/admin-gioco",
  PLATFORM_ADMIN: "#/admin-piattaforma",
};

const ETICHETTA_RUOLO = {
  PLAYER: "Giocatore",
  LOCALE_ADMIN: "Gestore di locale",
  GAME_ADMIN: "Admin gioco",
  PLATFORM_ADMIN: "Admin piattaforma",
};

let cleanupPaginaCorrente = null;

/**
 * Confronta l’hash con il modello della rotta ed estrae i parametri.
 *
 * @param {string} pattern Modello della rotta con eventuali parametri.
 * @param {string} hash Hash della destinazione richiesta.
 * @returns {?Object} Parametri della rotta, oppure null se non corrisponde.
 * 
 */
function abbina(pattern, hash) {
  const segmentiPattern = pattern.split("/");
  const segmentiHash = hash.split("/");
  if (segmentiPattern.length !== segmentiHash.length) {
    return null;
  }
  const parametri = {};
  for (let i = 0; i < segmentiPattern.length; i++) {
    if (segmentiPattern[i].startsWith(":")) {
      parametri[segmentiPattern[i].slice(1)] = decodeURIComponent(segmentiHash[i]);
    } else if (segmentiPattern[i] !== segmentiHash[i]) {
      return null;
    }
  }
  return parametri;
}

/**
 * Cerca la rotta corrispondente all’hash richiesto.
 *
 * @param {string} hash Hash della destinazione richiesta.
 * @returns {?Object} Rotta e parametri, oppure null.
 * 
 */
function risolvi(hash) {
  for (const route of ROUTES) {
    const parametri = abbina(route.pattern, hash);
    if (parametri) {
      return { route, parametri };
    }
  }
  return null;
}

/**
 * Sceglie la pagina iniziale in base al ruolo dell’utente corrente.
 *
 * @returns {string} Hash della pagina iniziale.
 * 
 */
function landingPredefinita() {
  const user = Auth.currentUser();
  return (user && LANDING_PER_RUOLO[user.role]) || "#/login";
}

/**
 * Aggiorna il menu e l’area utente secondo sessione e ruolo.
 *
 * 
 */
function renderMenu() {
  const menu = document.getElementById("menu");
  const areaUtente = document.getElementById("area-utente");
  const user = Auth.currentUser();
  const hashCorrente = location.hash || "#/login";

  menu.innerHTML = "";
  if (user) {
    for (const route of ROUTES) {
      if (!route.label || !route.ruoli.includes(user.role)) {
        continue;
      }
      const link = document.createElement("a");
      link.href = route.pattern;
      link.textContent = route.label;
      if (hashCorrente === route.pattern) {
        link.classList.add("attivo");
      }
      menu.appendChild(link);
    }
  }

  areaUtente.innerHTML = "";
  if (user) {
    areaUtente.innerHTML = `
      <div class="menu-utente">
        <button type="button" class="menu-utente-trigger" id="trigger-menu-utente" aria-haspopup="true" aria-expanded="false">
          <span class="user-avatar" aria-hidden="true"></span>
          <span class="user-details"><strong></strong><small></small></span>
          <span class="menu-utente-freccia">▾</span>
        </button>
        <div class="menu-utente-dropdown" id="dropdown-menu-utente">
          <a href="#/profilo">Il mio profilo</a>
          <button type="button" id="btn-esci">Esci</button>
        </div>
      </div>`;

    const trigger = areaUtente.querySelector("#trigger-menu-utente");
    const nomePubblico = user.role === "PLAYER" ? user.nickname || user.username : user.username;
    trigger.querySelector(".user-avatar").textContent = (nomePubblico || "?").slice(0, 1).toUpperCase();
    trigger.querySelector(".user-details strong").textContent = nomePubblico;
    trigger.querySelector(".user-details small").textContent = ETICHETTA_RUOLO[user.role] || user.role;
    const dropdown = areaUtente.querySelector("#dropdown-menu-utente");
    trigger.addEventListener("click", (evento) => {
      evento.stopPropagation();
      const aperto = dropdown.classList.toggle("aperto");
      trigger.setAttribute("aria-expanded", String(aperto));
    });
    dropdown.querySelector("a").addEventListener("click", () => {
      dropdown.classList.remove("aperto");
      trigger.setAttribute("aria-expanded", "false");
    });
    dropdown.querySelector("#btn-esci").addEventListener("click", async () => {
      if (typeof cleanupPaginaCorrente === "function") {
        try {
          cleanupPaginaCorrente();
        } catch {
        }
        cleanupPaginaCorrente = null;
      }
      await Auth.logout();
      location.hash = "#/login";
    });
  }
}

document.addEventListener("click", (evento) => {
  const contenitore = document.querySelector(".menu-utente");
  if (contenitore && !contenitore.contains(evento.target)) {
    const dropdown = document.getElementById("dropdown-menu-utente");
    const trigger = document.getElementById("trigger-menu-utente");
    if (dropdown) {
      dropdown.classList.remove("aperto");
    }
    if (trigger) {
      trigger.setAttribute("aria-expanded", "false");
    }
  }
});

/**
 * Mostra un messaggio di errore nell’area della pagina.
 *
 * @param {string} messaggio Messaggio da mostrare all’utente.
 * 
 */
function mostraErrorePagina(messaggio) {
  document.getElementById("app").innerHTML = `<div class="stato-errore">${messaggio}</div>`;
}

/**
 * Verifica l’accesso alla rotta, chiude la pagina precedente e carica quella richiesta.
 *
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function gestisciNavigazione() {
  if (typeof cleanupPaginaCorrente === "function") {
    try {
      cleanupPaginaCorrente();
    } catch {
    }
    cleanupPaginaCorrente = null;
  }

  let hash = location.hash || "#/login";
  const risolto = risolvi(hash);

  if (!risolto) {
    location.hash = landingPredefinita();
    return;
  }
  const { route, parametri } = risolto;

  if (!route.pubblica && !Auth.isAuthenticated()) {
    sessionStorage.setItem(DESTINAZIONE_DOPO_LOGIN, hash);
    location.hash = "#/login";
    return;
  }
  if (route.pubblica && Auth.isAuthenticated()) {
    location.hash = landingPredefinita();
    return;
  }
  if (route.ruoli && !Auth.hasRole(...route.ruoli)) {
    renderMenu();
    mostraErrorePagina("Permessi insufficienti per questa pagina.");
    return;
  }

  await Auth.caricaProfilo();
  if (location.hash && location.hash !== hash) return;
  renderMenu();
  const app = document.getElementById("app");
  document.body.classList.toggle("is-auth", Boolean(route.pubblica));
  app.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
  try {
    const modulo = await route.load();
    const risultato = await modulo.render(app, parametri);
    if (typeof risultato === "function") {
      cleanupPaginaCorrente = risultato;
    }
    if (Auth.currentUser()?.role === "PLAYER" && route.pattern !== "#/login") {
      try {
      const abbinamenti = await Api.get("/tornei/miei-abbinamenti");
      const nuovo = abbinamenti.find((item) => !item.letto && !item.riposo && !abbinamentoConcluso(item));
      if (nuovo) {
        const avviso = document.createElement("div");
        avviso.className = "card";
        avviso.setAttribute("role", "status");
        const testo = document.createElement("p");
        testo.textContent = `${nuovo.torneo}, turno ${nuovo.turno}: ${nuovo.incontro}. ${nuovo.locale}. `
          + (nuovo.scheduledAt ? `Partita ${new Date(nuovo.scheduledAt).toLocaleString()}, presentati entro ${new Date(nuovo.deadline).toLocaleString()}, bersaglio disponibile dalle ${new Date(nuovo.linkAt).toLocaleString()}.`
            : nuovo.homeAccepted || nuovo.awayAccepted ? "Accettazione registrata; in attesa dell'avversario." : "Accetta la sfida dalla pagina Tornei.")
          + (nuovo.esitoAssenza ? ` ${nuovo.esitoAssenza}.` : "");
        const link = document.createElement("a");
        link.href = "#/tornei";
        link.textContent = "Vedi i miei abbinamenti";
        const letto = document.createElement("button");
        letto.type = "button";
        letto.textContent = "Ho letto";
        letto.addEventListener("click", async () => {
          letto.disabled = true;
          try {
            await Api.post(`/tornei/avvisi/${nuovo.matchId}/letto`, {});
            avviso.remove();
          } catch (errore) {
            letto.disabled = false;
            testo.textContent = errore.message;
          }
        });
        avviso.append(testo, link, letto);
        app.prepend(avviso);
      }
      } catch (errore) {
        console.warn("Avvisi torneo non disponibili", errore);
      }
    }
  } catch (e) {
    console.error(e);
    mostraErrorePagina(e && e.message ? e.message : "Errore imprevisto nel caricamento della pagina.");
  }
}

export const Router = {
  aggiornaMenu: renderMenu,
  /**
   * Collega gli eventi del router e avvia la navigazione iniziale. 
   */
  avvia() {
    Auth.riprendiSessione();
    window.addEventListener("hashchange", gestisciNavigazione);
    gestisciNavigazione();
    setInterval(controllaAvvisiTorneo, 5000);
  },
  /**
   * Cambia l’hash per aprire la pagina richiesta. @param {string} hash Hash della destinazione richiesta.
   * 
   */
  vaiA(hash) {
    if (location.hash === hash) {
      gestisciNavigazione();
    } else {
      location.hash = hash;
    }
  },
  /**
   * Restituisce la pagina iniziale del ruolo corrente. @returns {string} Hash della pagina iniziale.
   * 
   */
  landingPerRuoloCorrente() {
    return landingPredefinita();
  },
  /**
   * Ripristina la destinazione salvata se accessibile al ruolo corrente. @returns {string} Hash da aprire dopo l’accesso.
   * 
   */
  destinazioneDopoLogin() {
    const hash = sessionStorage.getItem(DESTINAZIONE_DOPO_LOGIN);
    sessionStorage.removeItem(DESTINAZIONE_DOPO_LOGIN);
    return hash && risolvi(hash)?.route.ruoli?.includes(Auth.currentUser()?.role)
      ? hash : landingPredefinita();
  },
};
