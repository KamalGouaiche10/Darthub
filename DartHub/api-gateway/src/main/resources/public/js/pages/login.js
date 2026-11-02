import { Auth } from "../auth.js";
import { Router } from "../router.js";
import { attivaMostraPassword } from "../passwordVisibility.js";

/**
 * Prepara la pagina di accesso e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  container.innerHTML = `
    <section class="auth-layout grid">
      <div class="auth-story relative flex flex-col overflow-hidden">
        <div class="eyebrow inline-flex items-center gap-2"><span class="eyebrow-dot"></span> LA TUA ARENA DIGITALE</div>
        <h1>Ogni tiro<br>ha una <em>storia.</em></h1>
        <p>Dal primo lancio al bullseye decisivo: ritrova le tue partite, sfida gli amici e segui ogni progresso.</p>
        <div class="auth-story-points flex flex-col gap-3">
          <span><b>01</b> Trova un bersaglio</span>
          <span><b>02</b> Gioca in tempo reale</span>
          <span><b>03</b> Supera i tuoi record</span>
        </div>
        <div class="auth-art" aria-hidden="true">
          <div class="art-glow"></div>
          <div class="art-target"><div class="art-target-inner"><div class="art-bull"></div></div></div>
          <div class="art-flight art-flight-one"></div>
          <div class="art-flight art-flight-two"></div>
          <div class="art-note"><span class="eyebrow-dot"></span> IL PROSSIMO CENTRO È TUO</div>
        </div>
      </div>
      <div class="auth-panel flex flex-col">
        <div class="auth-panel-top flex items-center justify-between"><span class="eyebrow">BENTORNATO SU DARTHUB</span><span class="auth-spark">✳</span></div>
        <div class="auth-panel-content">
          <div class="auth-icon grid place-items-center" aria-hidden="true">↗</div>
          <h2>Pronto a giocare?</h2>
          <p class="auth-subtitle">Accedi al tuo spazio e riprendi da dove avevi lasciato.</p>
          <form id="form-login" class="auth-form">
            <label for="campo-username">Email o nome utente</label>
            <input id="campo-username" name="username" required autocomplete="username" placeholder="La tua email o il tuo username">
            <label for="campo-password">Password</label>
            <input id="campo-password" name="password" type="password" required autocomplete="current-password" placeholder="La tua password">
            <button type="submit" class="auth-submit flex w-full items-center justify-between">Entra nell'arena <span aria-hidden="true">↗</span></button>
          </form>
          <div id="errore-login"></div>
          <p class="auth-switch text-center">Non hai ancora un account? <a class="link-testuale" href="#/registrazione">Registrati <span aria-hidden="true">→</span></a></p>
        </div>
        <div class="auth-panel-footer">GIOCA. MIGLIORA. RIPETI. <span>© DARTHUB</span></div>
      </div>
    </section>`;

  const form = container.querySelector("#form-login");
  attivaMostraPassword(form);
  const zonaErrore = container.querySelector("#errore-login");

  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const bottone = form.querySelector('button[type="submit"]');
    bottone.disabled = true;
    zonaErrore.innerHTML = "";
    try {
      await Auth.login(form.username.value.trim(), form.password.value);
      Router.vaiA(Router.destinazioneDopoLogin());
    } catch (e) {
      zonaErrore.innerHTML = `<div class="stato-errore" style="margin-top: 1rem;">${e.message}</div>`;
    } finally {
      bottone.disabled = false;
    }
  });
}
