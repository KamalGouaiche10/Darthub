
const CACHE_NAME = "darthub-shell-v33";

const APP_SHELL = [
  "/",
  "/index.html",
  "/favicon.svg",
  "/css/tailwind.css",
  "/css/style.css",
  "/js/router.js",
  "/js/api.js",
  "/js/html.js",
  "/js/auth.js",
  "/js/localeStatistics.js",
  "/js/edgeRegistration.js",
  "/js/notify.js",
  "/js/liveScore.js",
  "/js/connectionBanner.js",
  "/js/tournamentDates.js",
  "/js/passwordVisibility.js",
  "/js/simulatorPanel.js",
  "/js/pages/login.js",
  "/js/pages/register.js",
  "/js/pages/profilo.js",
  "/js/pages/dartboards.js",
  "/js/pages/matchSetup.js",
  "/js/pages/matchLive.js",
  "/js/pages/matchHistory.js",
  "/js/pages/myStatistics.js",
  "/js/pages/tournaments.js",
  "/js/pages/localeAdmin.js",
  "/js/pages/gameAdmin.js",
  "/js/pages/platformAdmin.js",
  "/components/dartboardTarget.js",
  "/components/dataTable.js",
  "/components/standingsTable.js",
  "/components/scoreboard.js",
  "/lib/paho-mqtt.js",
];

const API_PREFIXES = [
  "/auth", "/users", "/locali", "/bersagli", "/edge-devices", "/local-edge", "/simulators", "/simulator",
  "/giochi", "/sensori", "/regole", "/partite", "/squadre",
  "/tornei", "/statistiche", "/health", "/client-config.json",
];

/**
 * Riconosce i percorsi delle API da escludere dalla cache del service worker.
 *
 * @param {string} pathname Percorso della richiesta.
 * @returns {boolean} True se il percorso appartiene alle API.
 * 
 */
function isChiamataApi(pathname) {
  return API_PREFIXES.some((prefisso) => pathname === prefisso || pathname.startsWith(prefisso + "/"));
}

self.addEventListener("install", (evento) => {
  evento.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nomi) => Promise.all(nomi.filter((nome) => nome !== CACHE_NAME).map((nome) => caches.delete(nome))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (evento) => {
  const url = new URL(evento.request.url);
  if (evento.request.method !== "GET" || url.origin !== self.location.origin || isChiamataApi(url.pathname)) {
    return;
  }

  evento.respondWith(fetch(evento.request)
    .then((rispostaRete) => {
      if (rispostaRete.ok) {
        const copia = rispostaRete.clone();
        evento.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.put(evento.request, copia)));
      }
      return rispostaRete;
    })
    .catch(async () => {
      const rispostaCache = await caches.match(evento.request);
      if (rispostaCache) return rispostaCache;
      throw new Error("Risorsa non disponibile offline");
    }));
});
