
/**
 * Mostra o nasconde l’avviso in base alla connessione del browser.
 *
 * 
 */
function aggiornaBanner() {
  const banner = document.getElementById("banner-offline");
  if (!banner) {
    return;
  }
  banner.hidden = navigator.onLine;
}

/**
 * Collega gli eventi di rete e aggiorna l’avviso di connessione.
 *
 * 
 */
export function avviaBannerConnessione() {
  window.addEventListener("online", aggiornaBanner);
  window.addEventListener("offline", aggiornaBanner);
  aggiornaBanner();
}
