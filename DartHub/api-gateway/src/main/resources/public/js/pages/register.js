
import { Api } from "../api.js";
import { Auth } from "../auth.js";
import { Router } from "../router.js";
import { notifica } from "../notify.js";
import { attivaMostraPassword } from "../passwordVisibility.js";

/**
 * Prepara la pagina di registrazione e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  container.innerHTML = `
    <section class="auth-layout auth-layout-register grid">
      <div class="auth-story relative flex flex-col overflow-hidden">
        <div class="eyebrow inline-flex items-center gap-2"><span class="eyebrow-dot"></span> ENTRA NEL GIOCO</div>
        <h1>Il prossimo<br>centro è <em>tuo.</em></h1>
        <p>Crea il tuo profilo, trova un bersaglio e trasforma ogni partita in un nuovo traguardo.</p>
        <div class="auth-story-points flex flex-col gap-3">
          <span><b>01</b> Scegli il tuo profilo</span>
          <span><b>02</b> Entra nella community</span>
          <span><b>03</b> Inizia a lanciare</span>
        </div>
        <div class="auth-art" aria-hidden="true">
          <div class="art-glow"></div>
          <div class="art-target"><div class="art-target-inner"><div class="art-bull"></div></div></div>
          <div class="art-flight art-flight-one"></div>
          <div class="art-flight art-flight-two"></div>
          <div class="art-note"><span class="eyebrow-dot"></span> LA SFIDA INIZIA QUI</div>
        </div>
      </div>
      <div class="auth-panel flex flex-col">
        <div class="auth-panel-top flex items-center justify-between"><span class="eyebrow">UNISCITI A DARTHUB</span><span class="auth-spark">✳</span></div>
        <div class="auth-panel-content">
          <div class="auth-icon grid place-items-center" aria-hidden="true">↗</div>
          <h2>Crea il tuo account</h2>
          <p class="auth-subtitle">Bastano pochi dettagli per iniziare.</p>
          <form id="form-registrazione" class="auth-form">
        <label for="campo-tipo-account">Tipo di account</label>
        <select id="campo-tipo-account" name="tipoAccount">
          <option value="PLAYER">Giocatore</option>
          <option value="LOCALE_ADMIN">Gestore di locale</option>
        </select>
        <label for="campo-username">Nome utente</label>
        <input id="campo-username" name="username" required autocomplete="username">
        <label for="campo-nome">Nome</label>
        <input id="campo-nome" name="firstName" required autocomplete="given-name">
        <label for="campo-cognome">Cognome</label>
        <input id="campo-cognome" name="lastName" required autocomplete="family-name">
        <label for="campo-email">Email</label>
        <input id="campo-email" name="email" type="email" required autocomplete="email">
        <div id="riga-nickname">
          <label for="campo-nickname">Nickname</label>
          <input id="campo-nickname" name="nickname" autocomplete="nickname">
        </div>
        <label for="campo-password">Password</label>
        <input id="campo-password" name="password" type="password" required autocomplete="new-password">
        <label for="campo-conferma">Conferma password</label>
        <input id="campo-conferma" name="conferma" type="password" required autocomplete="new-password">
        <div id="avviso-gestore" class="stato-vuoto" style="display: none;">
          Il tuo account nascerà "in attesa di approvazione": dopo l'accesso potrai inserire i dati
          dei tuoi locali e dei loro bersagli. Ogni locale diventerà operativo dopo
          l'approvazione dell'amministratore di piattaforma.
        </div>
        <button type="submit" class="auth-submit flex w-full items-center justify-between">Crea account <span aria-hidden="true">↗</span></button>
      </form>
      <div id="errore-registrazione"></div>
      <p class="auth-switch text-center">Hai già un account? <a class="link-testuale" href="#/login">Accedi <span aria-hidden="true">→</span></a></p>
        </div>
        <div class="auth-panel-footer">GIOCA. MIGLIORA. RIPETI. <span>© DARTHUB</span></div>
      </div>
    </section>`;

  const form = container.querySelector("#form-registrazione");
  attivaMostraPassword(form);
  const zonaErrore = container.querySelector("#errore-registrazione");
  const rigaNickname = container.querySelector("#riga-nickname");
  const campoNickname = container.querySelector("#campo-nickname");
  const avvisoGestore = container.querySelector("#avviso-gestore");

  /**
   * Adatta i campi di registrazione al tipo di account scelto. 
   */
  function aggiornaTipoAccount() {
    const gestore = form.tipoAccount.value === "LOCALE_ADMIN";
    rigaNickname.style.display = gestore ? "none" : "";
    campoNickname.required = !gestore;
    avvisoGestore.style.display = gestore ? "" : "none";
  }
  form.tipoAccount.addEventListener("change", aggiornaTipoAccount);
  aggiornaTipoAccount();

  form.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    zonaErrore.innerHTML = "";

    const username = form.username.value.trim();
    const password = form.password.value;
    const gestore = form.tipoAccount.value === "LOCALE_ADMIN";
    if (password !== form.conferma.value) {
      zonaErrore.innerHTML = '<div class="stato-errore" style="margin-top: 1rem;">Le due password non coincidono.</div>';
      return;
    }

    const bottone = form.querySelector('button[type="submit"]');
    bottone.disabled = true;
    try {
      await Api.post("/auth/register", {
        username,
        firstName: form.firstName.value.trim(),
        lastName: form.lastName.value.trim(),
        email: form.email.value.trim(),
        password,
        nickname: gestore ? null : form.nickname.value.trim(),
        role: gestore ? "LOCALE_ADMIN" : undefined,
      });
    } catch (e) {
      zonaErrore.innerHTML = `<div class="stato-errore" style="margin-top: 1rem;">${e.message}</div>`;
      bottone.disabled = false;
      return;
    }

    try {
      await Auth.login(username, password);
      notifica(gestore
        ? "Registrazione completata: puoi inserire i dati dei tuoi locali."
        : "Registrazione completata: benvenuto su DartHub!");
      Router.vaiA(Router.landingPerRuoloCorrente());
    } catch {
      notifica("Registrazione completata: accedi con le tue credenziali.");
      Router.vaiA("#/login");
    }
  });
}
