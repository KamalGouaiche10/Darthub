
import { Api } from "../api.js";
import { conferma } from "../uiDialog.js";
import { segueLive } from "../liveScore.js";
import { renderScoreboard } from "../../components/scoreboard.js";
import { creaBersaglio } from "../../components/dartboardTarget.js";
import { notificaErrore } from "../notify.js";
import { apriPannelloSimulatore, cambiaUrlPannelloSimulatore } from "../simulatorPanel.js";

const STATI_CONCLUSI = new Set(["COMPLETATA", "ANNULLATA", "SINCRONIZZATA"]);
const ETICHETTA_ESITO = { VALIDO: null, BUST: "BUST! Turno annullato", VITTORIA: "🏆 Vittoria!", FINE_PARTITA: "Partita terminata" };

/**
 * Monta il bersaglio della diretta e prepara il comando di evidenziazione.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Object} Comando per evidenziare la zona di un evento di tiro.
 * 
 */
function caricaTarget(container) {
  const bersaglio = creaBersaglio(container);
  return {
    /**
     * Evidenzia sul bersaglio la zona indicata dall’evento di tiro.
     *
     * @param {Object} evento Evento con settore e moltiplicatore del tiro.
     * @returns {void} Nessun valore restituito.
     * 
     */
    evidenzia: (evento) => bersaglio.evidenzia(evento.settore, evento.moltiplicatore) };
}

/**
 * Prepara la pagina della partita in diretta e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} opzioni Opzioni della funzione.
 * @param {(number|string)} opzioni.id Identificativo della risorsa.
 * @returns {Promise<(Function|undefined)>} Funzione di chiusura degli aggiornamenti, oppure undefined se non avviati.
 * 
 */
export async function render(container, { id }) {
  container.innerHTML = `
    <div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> LA PARTITA IN DIRETTA</div><h1>Partita #${id} <span id="indicatore-modalita" class="modalita-live"><span class="pallino"></span> connessione…</span></h1><p>Segui il punteggio mentre arrivano i tiri dal bersaglio.</p></div>
    <div class="griglia live-grid">
      <div class="card">
        <div id="bersaglio"></div>
        <p class="bersaglio-nota" style="color: var(--text-muted); font-size: 0.85rem; margin-top: 0.6rem;">
          Il bersaglio mostra i tiri in arrivo dal sensore collegato a questa partita (reale o
          simulato): non è cliccabile da qui.
        </p>
      </div>
      <div class="card">
        <h2>Punteggio</h2>
        <div id="area-scoreboard"><div class="stato-caricamento">Caricamento…</div></div>
        <div id="ultimo-tiro" style="margin-top: 1rem; color: var(--text-muted); min-height: 1.4em;"></div>
        <div id="area-azioni" style="margin-top: 1rem;"></div>
      </div>
    </div>`;

  const bersaglio = caricaTarget(container.querySelector("#bersaglio"));
  const indicatoreModalita = container.querySelector("#indicatore-modalita");
  const areaScoreboard = container.querySelector("#area-scoreboard");
  const zonaUltimoTiro = container.querySelector("#ultimo-tiro");
  const areaAzioni = container.querySelector("#area-azioni");

  let ultimoStatoVisto = null;
  let dartboardId = null;
  let localeId = null;
  let torneo = false;
  let legacyVirtual = false;
  let statoIniziale = null;
  try {
    const match = await Api.get(`/partite/${id}`);
    torneo = match.localUuid?.startsWith("tournament:") || false;
    if (torneo) await Api.get(`/tornei/partite/${id}/accesso`);
    legacyVirtual = torneo && !match.sensorTournament;
    statoIniziale = match.stato;
    if (legacyVirtual) container.querySelector(".bersaglio-nota").textContent =
      "Questa partita usa il bersaglio virtuale precedente. Aprilo dal pulsante accanto al punteggio per inserire i tiri.";
    ({ dartboardId, localeId } = match);
  } catch (errore) {
    container.innerHTML = '<div class="stato-errore" role="alert"></div><p><a href="#/tornei">Torna ai tornei</a></p>';
    container.querySelector(".stato-errore").textContent = errore.message;
    return;
  }

  /**
   * Mostra se la diretta riceve gli aggiornamenti via MQTT o polling. @param {string} modo Modalità di aggiornamento della diretta.
   * 
   */
  function aggiornaModalita(modo) {
    indicatoreModalita.className = `modalita-live attiva-${modo}`;
    indicatoreModalita.innerHTML =
      `<span class="pallino"></span> ${modo === "mqtt" ? "Live via MQTT" : "Aggiornamento ogni 2s (polling)"}`;
  }

  /**
   * Mostra le azioni consentite dallo stato e dal tipo di partita. @param {string} stato Stato dell’entità da visualizzare.
   * 
   */
  function disegnaAzioni(stato) {
    if (STATI_CONCLUSI.has(stato)) {
      areaAzioni.innerHTML = '<span class="badge badge-ok">Partita conclusa</span>';
      return;
    }
    areaAzioni.replaceChildren();
    if (torneo) {
      if (legacyVirtual) {
        areaAzioni.innerHTML = `<p>Questa partita è stata avviata con il bersaglio virtuale precedente. Il pannello del sensore non è collegato.</p><a href="#/tornei/partite/${id}/bersaglio">Apri bersaglio virtuale</a>`;
        return;
      }
      if (stato !== "IN_CORSO") {
        areaAzioni.innerHTML = "<p>La partita non è ancora iniziata. Puoi aprire il pannello del simulatore; i tiri saranno disponibili quando il bersaglio verrà collegato a questa partita.</p>";
      }
    } else {
      areaAzioni.innerHTML = '<div class="admin-row-actions"><button id="btn-fine-partita" class="pericolo">Termina senza vincitore e libera bersaglio</button></div>';
      areaAzioni.querySelector("#btn-fine-partita").addEventListener("click", async (evento) => {
        if (!await conferma("Terminare la partita senza assegnare una vittoria? Il punteggio sarà conservato e il bersaglio liberato.", "Termina partita")) return;
        evento.target.disabled = true;
        try {
          await Api.post(`/partite/${id}/fine`);
          ultimoStatoVisto = "COMPLETATA";
          disegnaAzioni("COMPLETATA");
        } catch (e) {
          notificaErrore(e);
          evento.target.disabled = false;
        }
      });
    }

    if (dartboardId != null) {
      areaAzioni.insertAdjacentHTML(
        "beforeend",
        '<div style="margin-top: 0.6rem;">'
          + '<button id="btn-apri-simulatore" type="button">🎯 Apri pannello simulatore</button> '
          + '<button id="btn-cambia-simulatore" type="button" class="secondario" title="Apri un simulatore esterno con un indirizzo personalizzato">Simulatore esterno</button>'
          + '</div><p>Il simulatore si avvia quando apri il pannello. Se è già acceso per questo bersaglio, viene riutilizzato.</p>',
      );
      areaAzioni.querySelector("#btn-apri-simulatore").addEventListener("click", () => apriPannelloSimulatore(dartboardId, localeId, Number(id)));
      areaAzioni.querySelector("#btn-cambia-simulatore").addEventListener("click", () => cambiaUrlPannelloSimulatore(dartboardId, localeId, Number(id)));
    }
  }

  /**
   * Aggiorna il tabellone e ridisegna le azioni quando cambia lo stato. @param {Object} scoreDto Stato e punteggi della partita.
   * 
   */
  function gestisciPunteggio(scoreDto) {
    renderScoreboard(areaScoreboard, scoreDto);
    if (scoreDto.stato !== ultimoStatoVisto) {
      ultimoStatoVisto = scoreDto.stato;
      disegnaAzioni(scoreDto.stato);
    }
  }

  /**
   * Evidenzia il tiro sul bersaglio e mostra l’esito ricevuto. @param {Object} evento Evento di tiro ricevuto.
   * 
   */
  function gestisciTiro(evento) {
    bersaglio.evidenzia(evento);
    const etichettaEsito = evento.esito ? ETICHETTA_ESITO[evento.esito] : null;
    const descrizione = evento.tipo === "TIRO" || !evento.tipo
      ? `${evento.settore} ${evento.moltiplicatore}`
      : evento.tipo;
    zonaUltimoTiro.innerHTML = `Ultimo evento: <strong>${descrizione}</strong> (${evento.punti} punti)`
      + (etichettaEsito ? ` — <span class="badge ${evento.esito === "BUST" ? "badge-danger" : "badge-ok"}">${etichettaEsito}</span>` : "");
  }

  disegnaAzioni(statoIniziale);
  ultimoStatoVisto = statoIniziale;
  const sessione = segueLive(id, { onTiro: gestisciTiro, onPunteggio: gestisciPunteggio,
    onModalita: aggiornaModalita,
    scorePath: torneo ? `/partite/${id}/bersaglio/punteggio` : undefined });

  return () => {
    sessione.ferma();
  };
}
