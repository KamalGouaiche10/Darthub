import { Api } from "../api.js";
import { Auth } from "../auth.js";
import { creaBersaglio } from "../../components/dartboardTarget.js";
import { renderScoreboard } from "../../components/scoreboard.js";
import { dataOraLocale } from "../tournamentDates.js";

/**
 * Prepara la pagina del bersaglio virtuale del torneo e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} opzioni Opzioni della funzione.
 * @param {(number|string)} opzioni.id Identificativo della risorsa.
 * @returns {Promise<(Function|undefined)>} Funzione di chiusura degli aggiornamenti, oppure undefined se non avviati.
 * 
 */
export async function render(container, { id }) {
  const matchId = Number(id);
  container.innerHTML = '<div class="stato-caricamento">Verifica del bersaglio della partita…</div>';
  if (!Number.isSafeInteger(matchId) || matchId <= 0) {
    container.innerHTML = '<div class="stato-errore">Partita del torneo non valida.</div><p><a href="#/tornei">Torna ai tornei</a></p>';
    return;
  }
  try {
    const accesso = await Api.get(`/tornei/partite/${matchId}/accesso`);
    if (Number(accesso.matchId) !== matchId) throw new Error("I dati del bersaglio non corrispondono alla partita.");
  } catch (e) {
    container.innerHTML = '<div class="stato-errore" role="alert"></div><p><a href="#/tornei">Torna ai tornei</a></p>';
    container.querySelector(".stato-errore").textContent = e.status === 403 && /disponibile/i.test(e.message)
      ? "Il bersaglio di questa partita non è ancora disponibile. Controlla l'orario nei tuoi abbinamenti."
      : e.status === 404
        ? "Il bersaglio di questa partita non è ancora stato assegnato."
        : e.message;
    return;
  }
  container.innerHTML = `<div class="page-intro"><h1>Bersaglio virtuale · partita #${matchId}</h1>
    <p>Questa partita è stata avviata con il bersaglio virtuale precedente. I tiri vanno inseriti qui; il pannello del sensore non è collegato a questa partita.</p><p><a href="#/tornei">Torna ai tornei</a></p></div>
    <p id="appointment-time"></p><div class="griglia live-grid"><div class="card" id="target"></div>
    <div class="card"><h2>Punteggio</h2><div id="score"></div><p id="target-status"></p></div></div>`;
  const status = container.querySelector("#target-status");
  const score = container.querySelector("#score");
  const appointmentTime = container.querySelector("#appointment-time");
  let active = false, myTurn = false, sending = false;
  let presenceRecorded = false;
  let refreshing = false;
  const target = creaBersaglio(container.querySelector("#target"), async (settore, moltiplicatore) => {
    if (!active || !myTurn || sending) return;
    sending = true;
    try {
      await Api.post(`/partite/${matchId}/bersaglio/tiri`, { settore, moltiplicatore });
      target.evidenzia(settore, moltiplicatore);
      await refresh();
    } catch (e) { status.textContent = e.message; }
    finally { sending = false; }
  });
  /**
   * Rilegge lo stato del bersaglio virtuale e aggiorna la partita mostrata. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function refresh() {
    if (refreshing) return;
    refreshing = true;
    try {
      const appointment = await Api.get(`/tornei/partite/${matchId}/appuntamento`);
      const now = Date.now(), at = new Date(appointment.scheduledAt).getTime();
      appointmentTime.textContent = `Inizio: ${dataOraLocale(appointment.scheduledAt)}.`;
      if (appointment.outcome === "VITTORIA_A_TAVOLINO" || appointment.outcome === "DOPPIA_ASSENZA"
          || appointment.matchStatus === "COMPLETATA") {
        active = false;
        status.textContent = appointment.outcome === "DOPPIA_ASSENZA" ? "Partita annullata: doppia assenza."
          : appointment.outcome === "VITTORIA_A_TAVOLINO" ? "Partita conclusa a tavolino."
            : "Partita conclusa.";
        return;
      }
      if (now < at) {
        active = false;
        const remaining = Math.ceil((at - now) / 1000);
        status.textContent = `Inizio tra ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}.`;
        return;
      }
      if (!presenceRecorded && appointment.matchStatus === "CREATA") {
        await Api.post(`/tornei/partite/${matchId}/pronto`, {});
        presenceRecorded = true;
      }
      await Api.get(`/tornei/partite/${matchId}/accesso`);
      const result = await Api.get(`/partite/${matchId}/bersaglio/punteggio`);
      renderScoreboard(score, result);
      active = result.stato === "IN_CORSO";
      const mine = result.punteggi?.find((p) => p.playerId === Auth.currentUser()?.userId);
      myTurn = active && mine?.participantId === result.partecipanteAttivoId;
      status.textContent = !active ? "Partita in attesa o conclusa."
        : myTurn ? "È il tuo turno: seleziona un settore." : "Turno dell'avversario.";
    } catch (e) { active = false; status.textContent = e.message; }
    finally { refreshing = false; }
  }
  await refresh();
  const timer = setInterval(refresh, 1000);
  return () => clearInterval(timer);
}
