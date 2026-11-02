
import { Api, queryString } from "../api.js";
import { Auth } from "../auth.js";
import { Router } from "../router.js";
import { notifica, notificaErrore } from "../notify.js";
import { caricaTutteLePagine } from "../../components/dataTable.js";
import { variantiPerGioco, indirizzoDelLocale } from "../matchOptions.js";

/**
 * Prepara la pagina di creazione della partita e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  const partecipanti = [{ tipo: "playerId", id: user.userId, etichetta: user.nickname || user.username }];

  container.innerHTML = `
    <div class="match-setup">
      <div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> PRONTI A LANCIARE</div><h1>Nuova partita.</h1><p>Imposta la sfida, scegli chi gioca e inizia a lanciare.</p></div>
      <div class="setup-grid">
        <section class="card setup-card" aria-labelledby="setup-config-title">
          <div class="setup-card-header">
            <span class="setup-step" aria-hidden="true">01</span>
            <div><h2 id="setup-config-title">Imposta la partita</h2><p>Scegli dove giocare e le regole della sfida.</p></div>
          </div>
          <div class="setup-fields">
            <div class="setup-field">
              <label for="select-locale">Locale</label>
              <select id="select-locale"><option value="">Caricamento…</option></select>
            </div>
            <div class="setup-field">
              <label for="select-bersaglio">Bersaglio</label>
              <select id="select-bersaglio" disabled><option value="">Scegli un locale</option></select>
            </div>
            <div class="setup-field">
              <label for="select-modalita">Modalità</label>
              <select id="select-modalita">
                <option value="INDIVIDUALE">Individuale</option>
                <option value="SQUADRA">Squadra</option>
              </select>
            </div>
          </div>
          <p class="setup-note">I bersagli e le varianti disponibili dipendono dal locale scelto.</p>
          <p id="indirizzo-locale" class="venue-address" aria-live="polite">Indirizzo non disponibile</p>
        </section>

        <section class="card setup-card" id="sezione-partecipanti" aria-labelledby="setup-players-title">
          <div class="setup-card-header">
            <span class="setup-step" aria-hidden="true">02</span>
            <div><h2 id="setup-players-title">Scegli i partecipanti</h2><p id="setup-players-help">Aggiungi almeno un avversario per iniziare.</p></div>
          </div>
          <div class="setup-list-heading"><span>In partita</span><span id="conteggio-partecipanti" aria-live="polite"></span></div>
          <div id="lista-partecipanti" class="setup-participants"></div>
          <div id="aggiungi-individuale" class="setup-add-panel">
            <label for="campo-player">Username o ID giocatore</label>
            <div class="setup-inline-control">
              <input id="campo-player" autocomplete="off" aria-describedby="player-hint errore-giocatore" placeholder="Es. player2 oppure 6">
              <button type="button" class="secondario" id="btn-aggiungi-player">Aggiungi</button>
            </div>
            <p class="setup-field-hint" id="player-hint">Il giocatore viene verificato prima di aggiungerlo.</p>
            <div id="errore-giocatore" role="alert"></div>
          </div>
          <div id="aggiungi-squadra" class="setup-add-panel" hidden>
            <label for="select-squadra">Squadra esistente</label>
            <div class="setup-inline-control">
              <select id="select-squadra"><option value="">Nessuna squadra disponibile</option></select>
              <button type="button" class="secondario" id="btn-aggiungi-squadra">Aggiungi</button>
            </div>
          </div>
          <details id="crea-squadra" class="setup-create-team" hidden>
            <summary>Crea una nuova squadra</summary>
            <div class="setup-inline-control">
              <div class="setup-field"><label for="campo-nome-squadra">Nome squadra</label><input id="campo-nome-squadra" placeholder="Es. I Frecciatori"></div>
              <button type="button" class="secondario" id="btn-crea-squadra">Crea squadra</button>
            </div>
          </details>
          <div class="setup-field">
            <label for="select-regola">Variante del gioco scelto</label>
            <select id="select-regola" disabled><option value="">Scegli un bersaglio</option></select>
          </div>
        </section>
      </div>
      <div class="setup-action-bar">
        <div><span class="setup-action-label" id="stato-partita">DA COMPLETARE</span><p id="riepilogo-partita" aria-live="polite">Scegli locale, bersaglio e variante per continuare.</p></div>
        <button id="btn-crea-partita">Crea e avvia la partita <span aria-hidden="true">↗</span></button>
      </div>
      <div id="errore-setup" role="alert"></div>
    </div>`;

  const selectLocale = container.querySelector("#select-locale");
  const selectBersaglio = container.querySelector("#select-bersaglio");
  const selectModalita = container.querySelector("#select-modalita");
  const selectRegola = container.querySelector("#select-regola");
  const listaPartecipanti = container.querySelector("#lista-partecipanti");
  const zonaErrore = container.querySelector("#errore-setup");
  const campoGiocatore = container.querySelector("#campo-player");
  const erroreGiocatore = container.querySelector("#errore-giocatore");
  const bottoneAggiungi = container.querySelector("#btn-aggiungi-player");
  const riepilogo = container.querySelector("#riepilogo-partita");
  const statoPartita = container.querySelector("#stato-partita");
  const indirizzoLocale = container.querySelector("#indirizzo-locale");
  let localiDisponibili = [];
  let richiestaBersagli = 0;
  let richiestaRegole = 0;

  /**
   * Mostra l’indirizzo del locale selezionato per la partita. 
   */
  function aggiornaIndirizzo() {
    indirizzoLocale.textContent = indirizzoDelLocale(localiDisponibili, selectLocale.value);
  }

  /**
   * Aggiorna il riepilogo della configurazione della nuova partita. 
   */
  function aggiornaRiepilogo() {
    const squadra = selectModalita.value === "SQUADRA";
    const quanti = partecipanti.filter((p) => p.tipo === (squadra ? "teamId" : "playerId")).length;
    if (!selectLocale.value || !selectBersaglio.value || !selectRegola.value) {
      statoPartita.textContent = "DA COMPLETARE";
      riepilogo.textContent = selectLocale.value
        ? `${selectLocale.selectedOptions[0].textContent} · ${indirizzoLocale.textContent} · Scegli bersaglio e variante per continuare.`
        : "Scegli locale, bersaglio e variante per continuare.";
      return;
    }
    const sfida = `${selectRegola.selectedOptions[0].textContent} · ${selectBersaglio.selectedOptions[0].textContent}`;
    statoPartita.textContent = quanti >= 2 ? "PRONTO A GIOCARE" : "MANCA UN AVVERSARIO";
    riepilogo.textContent = `${selectLocale.selectedOptions[0].textContent} · ${indirizzoLocale.textContent} · ${sfida} · ${quanti} ${squadra ? (quanti === 1 ? "squadra" : "squadre") : (quanti === 1 ? "giocatore" : "giocatori")}`;
  }

  /**
   * Mostra l’errore relativo alla selezione dei giocatori. @param {string} messaggio Messaggio da mostrare all’utente.
   * 
   */
  function mostraErroreGiocatore(messaggio) {
    erroreGiocatore.innerHTML = "";
    campoGiocatore.setAttribute("aria-invalid", messaggio ? "true" : "false");
    if (!messaggio) {
      return;
    }
    const avviso = document.createElement("div");
    avviso.className = "stato-errore";
    avviso.textContent = messaggio;
    erroreGiocatore.appendChild(avviso);
  }

  /**
   * Rimuove gli errori mostrati nel modulo della nuova partita. 
   */
  function pulisciErroreSetup() {
    zonaErrore.replaceChildren();
    container.querySelector(".setup-validation")?.remove();
    const campoInvalido = container.querySelector("[data-setup-invalid]");
    if (campoInvalido) {
      campoInvalido.removeAttribute("aria-invalid");
      if (campoInvalido.dataset.setupDescribedby) {
        campoInvalido.setAttribute("aria-describedby", campoInvalido.dataset.setupDescribedby);
      } else {
        campoInvalido.removeAttribute("aria-describedby");
      }
      delete campoInvalido.dataset.setupDescribedby;
      delete campoInvalido.dataset.setupInvalid;
    }
  }

  /**
   * Mostra un errore di configurazione ed evidenzia il campo associato. @param {string} messaggio Messaggio da mostrare all’utente.
   * @param {?HTMLElement} campo Controllo a cui associare l’errore.
   * 
   */
  function mostraErroreSetup(messaggio, campo) {
    pulisciErroreSetup();
    const avviso = document.createElement("div");
    avviso.className = campo ? "setup-validation" : "stato-errore";
    avviso.textContent = messaggio;
    if (campo) {
      avviso.id = "errore-setup-campo";
      avviso.setAttribute("role", "alert");
      campo.dataset.setupDescribedby = campo.getAttribute("aria-describedby") || "";
      campo.dataset.setupInvalid = "true";
      campo.setAttribute("aria-invalid", "true");
      campo.setAttribute("aria-describedby", `${campo.dataset.setupDescribedby} ${avviso.id}`.trim());
      (campo.closest(".setup-field, .setup-add-panel") || campo.parentElement).appendChild(avviso);
      campo.focus();
    } else {
      zonaErrore.appendChild(avviso);
    }
  }

  /**
   * Mostra i partecipanti selezionati e collega i comandi per rimuoverli. 
   */
  function disegnaPartecipanti() {
    const squadra = selectModalita.value === "SQUADRA";
    const visibili = partecipanti.map((p, indice) => ({ ...p, indice }))
      .filter((p) => p.tipo === (squadra ? "teamId" : "playerId"));
    const conteggio = visibili.length;
    container.querySelector("#conteggio-partecipanti").textContent =
      `${conteggio} ${squadra ? (conteggio === 1 ? "squadra" : "squadre") : (conteggio === 1 ? "giocatore" : "giocatori")}`;
    listaPartecipanti.replaceChildren();
    if (!conteggio) {
      const vuoto = document.createElement("p");
      vuoto.className = "setup-empty";
      vuoto.textContent = "Nessuna squadra aggiunta. Scegline almeno due.";
      listaPartecipanti.appendChild(vuoto);
    }
    visibili.forEach((p) => {
      const riga = document.createElement("div");
      riga.className = "setup-participant";
      const nome = document.createElement("span");
      nome.className = "setup-participant-name";
      nome.textContent = p.etichetta;
      riga.appendChild(nome);
      if (p.tipo === "playerId" && p.indice === 0) {
        const badge = document.createElement("span");
        badge.className = "setup-self-badge";
        badge.textContent = "Tu";
        riga.appendChild(badge);
      } else {
        const rimuovi = document.createElement("button");
        rimuovi.type = "button";
        rimuovi.className = "setup-remove";
        rimuovi.textContent = "Rimuovi";
        rimuovi.setAttribute("aria-label", `Rimuovi ${p.etichetta}`);
        rimuovi.addEventListener("click", () => {
          partecipanti.splice(p.indice, 1);
          disegnaPartecipanti();
        });
        riga.appendChild(rimuovi);
      }
      listaPartecipanti.appendChild(riga);
    });
    aggiornaRiepilogo();
  }
  disegnaPartecipanti();

  /**
   * Adatta i controlli dei partecipanti alla modalità di gioco selezionata. 
   */
  function aggiornaModalitaPartecipanti() {
    const squadra = selectModalita.value === "SQUADRA";
    container.querySelector("#aggiungi-individuale").hidden = squadra;
    container.querySelector("#aggiungi-squadra").hidden = !squadra;
    container.querySelector("#crea-squadra").hidden = !squadra;
    container.querySelector("#setup-players-help").textContent = squadra
      ? "Aggiungi almeno due squadre per iniziare."
      : "Aggiungi almeno un avversario per iniziare.";
    mostraErroreGiocatore("");
    disegnaPartecipanti();
    if (squadra) {
      caricaSquadre();
    }
  }

  /**
   * Carica i locali disponibili e aggiorna i controlli che li utilizzano. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function caricaLocali() {
    try {
      const locali = await Api.get("/locali" + queryString({ size: 100 }));
      localiDisponibili = locali.contenuto;
      selectLocale.innerHTML =
        '<option value="">— Scegli —</option>' +
        locali.contenuto.map((l) => `<option value="${l.id}">${l.nome}</option>`).join("");
      aggiornaRiepilogo();
    } catch (e) {
      selectLocale.innerHTML = '<option value="">Errore nel caricamento</option>';
      notificaErrore(e);
    }
  }

  /**
   * Carica i bersagli del locale e aggiorna le relative opzioni o schede. @param {(number|string)} localeId Identificativo del locale.
   * @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function caricaBersagli(localeId) {
    const richiesta = ++richiestaBersagli;
    ++richiestaRegole;
    selectBersaglio.disabled = true;
    selectBersaglio.innerHTML = '<option value="">Caricamento…</option>';
    selectRegola.disabled = true;
    selectRegola.innerHTML = '<option value="">Scegli un bersaglio</option>';
    try {
      const bersagli = await Api.get(`/locali/${localeId}/bersagli` + queryString({ size: 50 }));
      if (richiesta !== richiestaBersagli || selectLocale.value !== localeId) return;
      if (!bersagli.contenuto.length) {
        selectBersaglio.innerHTML = '<option value="">Nessun bersaglio in questo locale</option>';
        aggiornaRiepilogo();
        return;
      }
      selectBersaglio.innerHTML = '<option value="">— Scegli un bersaglio disponibile —</option>' + bersagli.contenuto
        .map(
          (b) =>
            `<option value="${b.id}" data-game-type="${b.gameTypeId}" ${b.prenotabile ? "" : "disabled"}>${b.identificativoLocale} (${b.prenotabile ? "DISPONIBILE" : b.stato !== "DISPONIBILE" ? b.stato : b.statoEdge === "ASSENTE" ? "EDGE ASSENTE" : "EDGE OFFLINE"})</option>`,
        )
        .join("");
      selectBersaglio.disabled = false;
      aggiornaRiepilogo();
    } catch (e) {
      if (richiesta !== richiestaBersagli) return;
      selectBersaglio.innerHTML = '<option value="">Errore nel caricamento</option>';
      notificaErrore(e);
    }
  }

  /**
   * Carica le regole del gioco e aggiorna i controlli della pagina. @param {(number|string)} gameTypeId Identificativo del tipo di gioco.
   * @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function caricaRegole(gameTypeId) {
    const richiesta = ++richiestaRegole;
    selectRegola.disabled = true;
    selectRegola.innerHTML = '<option value="">Caricamento…</option>';
    try {
      const regole = await caricaTutteLePagine((page, size) =>
        Api.get(`/giochi/${gameTypeId}/regole` + queryString({ page, size })));
      if (richiesta !== richiestaRegole || selectBersaglio.selectedOptions[0]?.dataset.gameType !== gameTypeId) return;
      if (!regole.contenuto.length) {
        selectRegola.innerHTML = '<option value="">Nessuna variante disponibile per questo gioco</option>';
        aggiornaRiepilogo();
        return;
      }
      selectRegola.innerHTML = variantiPerGioco(regole.contenuto, gameTypeId)
        .map((r) => `<option value="${r.id}">${r.variante} (${r.direzione === "DECRESCENTE" ? "da " + r.punteggioIniziale : "verso " + r.punteggioObiettivo})</option>`)
        .join("");
      if (!selectRegola.options.length) selectRegola.innerHTML = '<option value="">Nessuna variante disponibile per questo gioco</option>';
      selectRegola.disabled = !selectRegola.value;
      aggiornaRiepilogo();
    } catch (e) {
      if (richiesta !== richiestaRegole) return;
      selectRegola.innerHTML = '<option value="">Errore nel caricamento</option>';
      notificaErrore(e);
    }
  }

  /**
   * Carica le squadre dell’utente e aggiorna il selettore. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function caricaSquadre() {
    const select = container.querySelector("#select-squadra");
    try {
      const squadre = await Api.get("/squadre" + queryString({ size: 100 }));
      select.innerHTML = squadre.contenuto.length
        ? squadre.contenuto.map((s) => `<option value="${s.id}">${s.nome}</option>`).join("")
        : '<option value="">Nessuna squadra: creane una</option>';
    } catch {
      select.innerHTML = '<option value="">Errore nel caricamento</option>';
    }
  }

  await caricaLocali();
  aggiornaModalitaPartecipanti();

  selectLocale.addEventListener("change", () => {
    pulisciErroreSetup();
    aggiornaIndirizzo();
    if (selectLocale.value) {
      caricaBersagli(selectLocale.value);
    } else {
      ++richiestaBersagli;
      ++richiestaRegole;
      selectBersaglio.disabled = true;
      selectBersaglio.innerHTML = '<option value="">Scegli un locale</option>';
      selectRegola.disabled = true;
      selectRegola.innerHTML = '<option value="">Scegli un bersaglio</option>';
    }
    aggiornaRiepilogo();
  });
  selectBersaglio.addEventListener("change", () => {
    pulisciErroreSetup();
    const gameTypeId = selectBersaglio.selectedOptions[0]?.dataset.gameType;
    if (gameTypeId) {
      caricaRegole(gameTypeId);
    } else {
      ++richiestaRegole;
      selectRegola.disabled = true;
      selectRegola.innerHTML = '<option value="">Scegli un bersaglio</option>';
    }
    aggiornaRiepilogo();
  });
  selectModalita.addEventListener("change", () => {
    pulisciErroreSetup();
    aggiornaModalitaPartecipanti();
  });
  selectRegola.addEventListener("change", () => {
    pulisciErroreSetup();
    aggiornaRiepilogo();
  });

  /**
   * Cerca un giocatore e prepara identificativo ed etichetta per la selezione. @param {string} valore Identificativo numerico o nome utente del giocatore.
   * @returns {Promise<Object>} Identificativo ed etichetta del giocatore trovato.
   * @throws {Error} Se il giocatore non può essere individuato.
   * 
   */
  async function risolviGiocatore(valore) {
    const perId = /^\d+$/.test(valore);
    const utente = await Api.get("/users/lookup" + queryString(perId ? { id: valore } : { username: valore }));
    return { id: utente.id, etichetta: `${utente.nickname || utente.username} (${utente.username})` };
  }

  /**
   * Valida la scelta del giocatore e lo aggiunge ai partecipanti. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function aggiungiGiocatore() {
    const valore = campoGiocatore.value.trim();
    mostraErroreGiocatore("");
    if (!valore) {
      mostraErroreGiocatore("Inserisci l'ID o lo username del giocatore da aggiungere.");
      return;
    }
    if (/^\d+$/.test(valore) && Number(valore) <= 0) {
      mostraErroreGiocatore("Identificativo non valido: deve essere un numero maggiore di zero.");
      return;
    }

    bottoneAggiungi.disabled = true;
    try {
      const { id, etichetta } = await risolviGiocatore(valore);
      if (partecipanti.some((p) => p.tipo === "playerId" && p.id === id)) {
        mostraErroreGiocatore(`${etichetta} è già tra i partecipanti.`);
        return;
      }
      partecipanti.push({ tipo: "playerId", id, etichetta });
      campoGiocatore.value = "";
      pulisciErroreSetup();
      disegnaPartecipanti();
    } catch (e) {
      mostraErroreGiocatore(
        e.status === 404
          ? `Utente non trovato: nessun giocatore con ${/^\d+$/.test(valore) ? `ID ${valore}` : `username "${valore}"`}.`
          : e.message,
      );
    } finally {
      bottoneAggiungi.disabled = false;
    }
  }

  bottoneAggiungi.addEventListener("click", aggiungiGiocatore);
  campoGiocatore.addEventListener("keydown", (evento) => {
    if (evento.key === "Enter") {
      evento.preventDefault();
      aggiungiGiocatore();
    }
  });
  campoGiocatore.addEventListener("input", () => mostraErroreGiocatore(""));

  container.querySelector("#btn-aggiungi-squadra").addEventListener("click", () => {
    const select = container.querySelector("#select-squadra");
    if (!select.value) {
      return;
    }
    const id = Number(select.value);
    if (partecipanti.some((p) => p.tipo === "teamId" && p.id === id)) {
      notifica("Questa squadra è già stata aggiunta", "errore");
      return;
    }
    partecipanti.push({ tipo: "teamId", id, etichetta: select.selectedOptions[0].textContent });
    pulisciErroreSetup();
    disegnaPartecipanti();
  });

  container.querySelector("#btn-crea-squadra").addEventListener("click", async () => {
    const campo = container.querySelector("#campo-nome-squadra");
    if (!campo.value.trim()) {
      return;
    }
    try {
      const squadra = await Api.post("/squadre", { nome: campo.value.trim() });
      notifica(`Squadra "${squadra.nome}" creata`);
      partecipanti.push({ tipo: "teamId", id: squadra.id, etichetta: squadra.nome });
      pulisciErroreSetup();
      disegnaPartecipanti();
      campo.value = "";
      container.querySelector("#crea-squadra").open = false;
      await caricaSquadre();
    } catch (e) {
      notificaErrore(e);
    }
  });

  container.querySelector("#btn-crea-partita").addEventListener("click", async () => {
    pulisciErroreSetup();
    if (!selectBersaglio.value || !selectRegola.value) {
      mostraErroreSetup(selectBersaglio.value && selectRegola.selectedOptions[0]?.textContent.includes("Nessuna variante")
        ? "Nessuna variante disponibile per il gioco di questo bersaglio."
        : "Scegli locale, bersaglio e variante prima di continuare.",
        !selectLocale.value ? selectLocale : !selectBersaglio.value ? selectBersaglio : selectRegola);
      return;
    }
    if (selectModalita.value === "SQUADRA" && partecipanti.filter((p) => p.tipo === "teamId").length < 2) {
      mostraErroreSetup("Servono almeno due squadre per una partita a squadre.", container.querySelector("#select-squadra"));
      return;
    }
    if (selectModalita.value === "INDIVIDUALE" && partecipanti.filter((p) => p.tipo === "playerId").length < 2) {
      mostraErroreSetup("Aggiungi almeno un avversario.", campoGiocatore);
      return;
    }

    const bottone = container.querySelector("#btn-crea-partita");
    bottone.disabled = true;
    bottone.textContent = "Avvio in corso…";
    let partita;
    try {
      const bersagliAggiornati = await Api.get(`/locali/${selectLocale.value}/bersagli` + queryString({ size: 100 }));
      if (!bersagliAggiornati.contenuto.some((b) => b.id === Number(selectBersaglio.value) && b.prenotabile)) {
        await caricaBersagli(selectLocale.value);
        throw new Error("Il bersaglio non è più disponibile. Scegli un bersaglio con edge online.");
      }
      const gameTypeId = selectBersaglio.selectedOptions[0].dataset.gameType;
      partita = await Api.post("/partite", {
        dartboardId: Number(selectBersaglio.value),
        localeId: Number(selectLocale.value),
        gameTypeId: Number(gameTypeId),
        scoringRuleId: Number(selectRegola.value),
        modalita: selectModalita.value,
      });

      const daAggiungere = partecipanti.filter((p) => p.tipo === (selectModalita.value === "SQUADRA" ? "teamId" : "playerId"));
      for (const p of daAggiungere) {
        await Api.post(`/partite/${partita.id}/partecipanti`, {
          playerId: p.tipo === "playerId" ? p.id : null,
          teamId: p.tipo === "teamId" ? p.id : null,
        });
      }
      await Api.post(`/partite/${partita.id}/avvia`);
      notifica("Partita avviata!");
      Router.vaiA(`#/partite/${partita.id}`);
    } catch (e) {
      if (partita?.id) {
        try {
          const stato = await Api.get(`/partite/${partita.id}`);
          if (stato.stato === "CREATA") await Api.del(`/partite/${partita.id}/bozza`);
          if (stato.stato === "IN_CORSO") {
            Router.vaiA(`#/partite/${partita.id}`);
            return;
          }
        } catch {
          try {
            if ((await Api.get(`/partite/${partita.id}`)).stato === "IN_CORSO") {
              Router.vaiA(`#/partite/${partita.id}`);
              return;
            }
          } catch {   }
        }
      }
      mostraErroreSetup(e.codice === "EDGE_OFFLINE"
        ? "Il dispositivo edge è offline. Verifica che edge-component sia avviato e connesso al broker; dopo il primo heartbeat potrai avviare la partita."
        : e.message);
    } finally {
      bottone.disabled = false;
      bottone.innerHTML = 'Crea e avvia la partita <span aria-hidden="true">↗</span>';
    }
  });
}
