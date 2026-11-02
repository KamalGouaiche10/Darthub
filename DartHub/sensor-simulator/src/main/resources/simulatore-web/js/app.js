import { creaBersaglio } from "../components/dartboardTarget.js";
import { SimulatorApi } from "./simulatorClient.js";

const elementoIdentificativo = document.getElementById("bersaglio-identificativo");
const elementoStato = document.getElementById("stato-connessione");
const elementoBatteria = document.getElementById("stato-batteria");
const elementoBatteriaLivello = document.getElementById("batteria-livello");
const elementoEsecuzione = document.getElementById("stato-esecuzione");
const elementoUltimoTiro = document.getElementById("ultimo-tiro");
const selectScenario = document.getElementById("select-scenario");
const inputVelocita = document.getElementById("input-velocita");
const inputDisconnetti = document.getElementById("input-disconnetti");
const bottoneAvvia = document.getElementById("btn-avvia");
const bottoneFerma = document.getElementById("btn-ferma");
const bottoneLancia = document.getElementById("btn-lancia-freccetta");
const elementoMessaggio = document.getElementById("messaggio");

let bersaglio = null;
let identificativoMostrato = false;
let statoCaricato = false;
let lancioInCorso = false;
const richiesta = new URLSearchParams(location.search);
const dartboardAtteso = richiesta.get("dartboardId");
const localeAtteso = richiesta.get("localeId");
const matchAtteso = richiesta.get("matchId");
let bersaglioCorretto = dartboardAtteso == null && localeAtteso == null && matchAtteso == null;
let bersaglioErratoPrima = false;
if (localeAtteso === "5" && dartboardAtteso === "7"
    && ["localhost", "127.0.0.1"].includes(location.hostname) && location.port === "7200") {
  const corretto = new URL(location.href);
  corretto.port = "7207";
  location.replace(corretto.href);
}
const NOMI_SCENARIO = { RANDOM: "Casuale", PARTITA_501: "Partita 501", SEQUENZA: "Sequenza" };

/**
 * Mostra nel pannello del simulatore un messaggio di stato o errore.
 *
 * @param {string} testo Testo da visualizzare o cercare.
 * @param {boolean} errore Indica se mostrare il messaggio come errore.
 * 
 */
function mostraMessaggio(testo, errore) {
  elementoMessaggio.textContent = testo;
  elementoMessaggio.classList.toggle("errore", Boolean(errore));
}

/**
 * Invia il tiro al simulatore, aggiorna il pannello e segnala eventuali errori.
 *
 * @param {string} settore Codice del settore colpito.
 * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
 * @returns {Promise<boolean>} True se il tiro è stato inviato, false se il bersaglio non corrisponde o l’invio fallisce.
 * 
 */
async function gestisciTiro(settore, moltiplicatore) {
  if (!bersaglioCorretto) {
    mostraMessaggio("Questo simulatore appartiene a un altro bersaglio. Apri quello della partita corrente.", true);
    return false;
  }
  try {
    const tiro = await SimulatorApi.tiro(settore, moltiplicatore);
    bersaglio.evidenzia(tiro.settore, tiro.moltiplicatore);
    elementoUltimoTiro.textContent = `${tiro.settore} ${tiro.moltiplicatore} · ${tiro.punti} punti`;
    mostraMessaggio(`Tiro inviato: ${tiro.settore} ${tiro.moltiplicatore} (${tiro.punti} punti). Il punteggio cambia se una partita è in corso su questo bersaglio.`, false);
    return true;
  } catch (e) {
    mostraMessaggio(`Tiro fallito: ${e.message}`, true);
    return false;
  }
}

/**
 * Legge lo stato del simulatore e aggiorna i controlli del pannello.
 *
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
async function aggiornaStato() {
  try {
    const stato = await SimulatorApi.stato();
    statoCaricato = true;
    bersaglioCorretto = (dartboardAtteso == null || Number(dartboardAtteso) === stato.dartboardId)
      && (localeAtteso == null || Number(localeAtteso) === stato.localeId)
      && (matchAtteso == null || Number(matchAtteso) === stato.matchId);
    if (!bersaglioCorretto) {
      mostraMessaggio(`Questo simulatore è collegato al locale ${stato.localeId}, bersaglio ${stato.dartboardId}, partita ${stato.matchId ?? "nessuna"}. La partita richiesta è ${matchAtteso ?? "non specificata"}: attendi l'avvio o apri il simulatore del bersaglio corretto.`, true);
    } else if (bersaglioErratoPrima) {
      mostraMessaggio(`Simulatore collegato alla partita #${stato.matchId}. Puoi iniziare a tirare.`, false);
    }
    bersaglioErratoPrima = !bersaglioCorretto;
    bottoneLancia.disabled = !bersaglioCorretto || lancioInCorso;
    if (!identificativoMostrato) {
      identificativoMostrato = true;
      elementoIdentificativo.textContent = `Locale ${stato.localeId} · Bersaglio ${stato.dartboardId}`;
      document.title = `DartHub — Sensore ${stato.localeId}/${stato.dartboardId}`;
    }
    elementoStato.textContent = stato.connesso ? "connesso" : "disconnesso";
    elementoStato.classList.toggle("ok", stato.connesso);
    elementoStato.classList.toggle("ko", !stato.connesso);
    elementoStato.closest(".metric-card").classList.toggle("is-online", stato.connesso);
    elementoStato.closest(".metric-card").classList.toggle("is-offline", !stato.connesso);
    elementoBatteria.textContent = `${stato.batteria}%`;
    elementoBatteriaLivello.style.width = `${Math.max(0, Math.min(100, Number(stato.batteria) || 0))}%`;
    elementoEsecuzione.textContent = stato.inEsecuzione ? "in esecuzione" : "fermo";
    elementoEsecuzione.closest(".metric-card").classList.toggle("is-running", stato.inEsecuzione);
    bottoneAvvia.disabled = !bersaglioCorretto || stato.inEsecuzione;
    bottoneFerma.disabled = !bersaglioCorretto || !stato.inEsecuzione;
    if (document.activeElement !== selectScenario) {
      selectScenario.value = stato.scenario;
    }
    if (document.activeElement !== inputVelocita) {
      inputVelocita.value = stato.intervalloMs;
    }
  } catch (e) {
    mostraMessaggio(e.message, true);
    statoCaricato = false;
    bottoneLancia.disabled = true;
    elementoStato.textContent = "irraggiungibile";
    elementoStato.classList.remove("ok");
    elementoStato.classList.add("ko");
    elementoStato.closest(".metric-card").classList.remove("is-online");
    elementoStato.closest(".metric-card").classList.add("is-offline");
    elementoBatteria.textContent = "—";
    elementoBatteriaLivello.style.width = "0%";
    elementoEsecuzione.textContent = "non disponibile";
    elementoEsecuzione.closest(".metric-card").classList.remove("is-running");
    bottoneAvvia.disabled = true;
    bottoneFerma.disabled = true;
  }
}

document.getElementById("btn-avvia").addEventListener("click", async () => {
  try {
    await SimulatorApi.avvia();
    mostraMessaggio("Loop automatico avviato", false);
    aggiornaStato();
  } catch (e) {
    mostraMessaggio(`Avvio fallito: ${e.message}`, true);
  }
});

document.getElementById("btn-ferma").addEventListener("click", async () => {
  try {
    await SimulatorApi.ferma();
    mostraMessaggio("Loop automatico fermato", false);
    aggiornaStato();
  } catch (e) {
    mostraMessaggio(`Arresto fallito: ${e.message}`, true);
  }
});

selectScenario.addEventListener("change", async () => {
  try {
    await SimulatorApi.scenario(selectScenario.value);
    mostraMessaggio(`Scenario impostato: ${NOMI_SCENARIO[selectScenario.value] || selectScenario.value}`, false);
  } catch (e) {
    mostraMessaggio(`Cambio scenario fallito: ${e.message}`, true);
  }
});

document.getElementById("btn-velocita").addEventListener("click", async () => {
  const intervalloMs = Number(inputVelocita.value);
  if (!Number.isFinite(intervalloMs) || intervalloMs < 50) {
    mostraMessaggio("Intervallo non valido: minimo 50ms (valori più bassi rischiano di sovraccaricare il broker MQTT)", true);
    return;
  }
  try {
    await SimulatorApi.velocita(intervalloMs);
    mostraMessaggio(`Velocità impostata: ${intervalloMs}ms`, false);
  } catch (e) {
    mostraMessaggio(`Cambio velocità fallito: ${e.message}`, true);
  }
});

document.getElementById("btn-disconnetti").addEventListener("click", async () => {
  const secondi = Number(inputDisconnetti.value);
  if (!Number.isFinite(secondi) || secondi <= 0) {
    mostraMessaggio("Numero di secondi non valido", true);
    return;
  }
  try {
    await SimulatorApi.disconnetti(secondi);
    mostraMessaggio(`Disconnessione simulata per ${secondi}s`, false);
    aggiornaStato();
  } catch (e) {
    mostraMessaggio(`Disconnessione fallita: ${e.message}`, true);
  }
});

bersaglio = creaBersaglio(document.getElementById("bersaglio"), gestisciTiro);

bottoneLancia.addEventListener("click", async () => {
  lancioInCorso = true;
  bottoneLancia.disabled = true;
  try {
    await bersaglio.lanciaCasuale();
  } catch (e) {
    mostraMessaggio(`Lancio fallito: ${e.message}`, true);
  } finally {
    lancioInCorso = false;
    bottoneLancia.disabled = !statoCaricato || !bersaglioCorretto;
  }
});

document.addEventListener("keydown", (evento) => {
  if (evento.defaultPrevented || evento.code !== "Space" || evento.repeat) return;
  const attivo = document.activeElement;
  if (attivo?.closest?.("input, select, textarea, button, [contenteditable], [data-settore]")) return;
  evento.preventDefault();
  bersaglio.tiraSelezionato();
});

aggiornaStato();
setInterval(aggiornaStato, 2000);
