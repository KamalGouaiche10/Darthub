
import { Api } from "../api.js";
import { Auth } from "../auth.js";
import { Router } from "../router.js";
import { notifica, notificaErrore } from "../notify.js";
import { attivaMostraPassword, nascondiPassword } from "../passwordVisibility.js";

/**
 * Prepara la pagina del profilo e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  container.innerHTML = `<div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> IL TUO SPAZIO</div><h1>Il mio profilo.</h1><p>Gestisci i dettagli del tuo account DartHub.</p></div><div id="contenuto"><div class="stato-caricamento">Caricamento…</div></div>`;
  const contenuto = container.querySelector("#contenuto");
  contenuto.classList.add("profile-grid");

  let dati;
  try {
    dati = await Api.get(`/users/${user.userId}`);
  } catch (e) {
    contenuto.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    return;
  }

  const mostraNickname = dati.role === "PLAYER";
  contenuto.innerHTML = `
    <div class="card profile-card">
      <h2>Dati account</h2>
      <form id="form-dati">
        <label for="campo-username">Nome utente</label>
        <input id="campo-username" disabled>
        <label for="campo-nome">Nome</label>
        <input id="campo-nome" name="firstName" autocomplete="given-name">
        <label for="campo-cognome">Cognome</label>
        <input id="campo-cognome" name="lastName" autocomplete="family-name">
        <label for="campo-ruolo">Ruolo</label>
        <input id="campo-ruolo" disabled>
        <label for="campo-email">Email</label>
        <input id="campo-email" name="email" type="email" required autocomplete="email">
        ${mostraNickname
          ? `<label for="campo-nickname">Nickname</label>
        <input id="campo-nickname" name="nickname" required autocomplete="nickname">`
          : ""}
        <button type="submit" style="margin-top: 1rem;">Salva modifiche</button>
      </form>
    </div>

    <div class="card profile-card">
      <h2>Cambia password</h2>
      <form id="form-password">
        <label for="campo-vecchia">Password attuale</label>
        <input id="campo-vecchia" name="vecchia" type="password" required autocomplete="current-password">
        <label for="campo-nuova">Nuova password</label>
        <input id="campo-nuova" name="nuova" type="password" required autocomplete="new-password">
        <label for="campo-conferma">Conferma nuova password</label>
        <input id="campo-conferma" name="conferma" type="password" required autocomplete="new-password">
        <button type="submit" style="margin-top: 1rem;">Cambia password</button>
      </form>
    </div>`;

  const formDati = contenuto.querySelector("#form-dati");
  formDati.querySelector("#campo-username").value = dati.username;
  formDati.firstName.value = dati.firstName || "";
  formDati.lastName.value = dati.lastName || "";
  formDati.querySelector("#campo-ruolo").value = dati.role;
  formDati.email.value = dati.email || "";
  if (mostraNickname) {
    formDati.nickname.value = dati.nickname || "";
  }

  formDati.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const bottone = formDati.querySelector("button");
    bottone.disabled = true;
    try {
      const aggiornato = await Api.put(`/users/${user.userId}`, {
        email: formDati.email.value.trim(),
        firstName: formDati.firstName.value.trim(),
        lastName: formDati.lastName.value.trim(),
        nickname: mostraNickname ? formDati.nickname.value.trim() : null,
        avatarUrl: null,
      });
      dati = aggiornato;
      Auth.aggiornaProfilo(aggiornato);
      if (mostraNickname) formDati.nickname.value = aggiornato.nickname;
      Router.aggiornaMenu();
      notifica("Profilo aggiornato");
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });

  const formPassword = contenuto.querySelector("#form-password");
  attivaMostraPassword(formPassword);
  formPassword.addEventListener("submit", async (evento) => {
    evento.preventDefault();
    if (formPassword.nuova.value !== formPassword.conferma.value) {
      notifica("Le due password non coincidono", "errore");
      return;
    }
    const bottone = formPassword.querySelector('button[type="submit"]');
    bottone.disabled = true;
    try {
      await Api.patch(`/users/${user.userId}/password`, {
        oldPassword: formPassword.vecchia.value,
        newPassword: formPassword.nuova.value,
      });
      notifica("Password aggiornata");
      formPassword.reset();
      nascondiPassword(formPassword);
    } catch (e) {
      notificaErrore(e);
    } finally {
      bottone.disabled = false;
    }
  });
}
