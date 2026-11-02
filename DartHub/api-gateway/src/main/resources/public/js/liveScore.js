
import { Api } from "./api.js";

const PORTA_WEBSOCKET = 9001;
const TOPIC_EVENTO = "eventi/partite/evento";
const INTERVALLO_POLLING_MS = 2000;

/**
 * Segue punteggio e tiri tramite MQTT, con polling quando necessario.
 *
 * @param {(number|string)} matchId Identificativo della partita.
 * @param {Object} [opzioni] Opzioni della funzione.
 * @param {?Function} [opzioni.onTiro] Gestore richiamato con settore e moltiplicatore del tiro.
 * @param {Function} [opzioni.onPunteggio] Gestore che riceve lo stato del punteggio.
 * @param {Function} [opzioni.onModalita] Gestore che riceve la modalità MQTT o polling.
 * @param {string} [opzioni.scorePath] Percorso alternativo per leggere il punteggio.
 * @returns {Object} Sessione con il comando ferma per chiudere gli aggiornamenti.
 * @see Api.get
 * 
 */
export function segueLive(matchId, { onTiro, onPunteggio, onModalita, scorePath } = {}) {
  let modalitaCorrente = null;
  let client = null;
  let timerPolling = null;
  let timerPunteggio = null;
  let fermato = false;
  let ultimoTiroIdVisto = null;
  let primoControlloTiri = true;

  /**
   * Aggiorna la modalità di connessione e avvisa il chiamante se cambia. @param {string} nuova Nuova modalità di connessione.
   * 
   */
  function impostaModalita(nuova) {
    if (modalitaCorrente !== nuova) {
      modalitaCorrente = nuova;
      if (onModalita) {
        onModalita(nuova);
      }
    }
  }

  /**
   * Legge il punteggio corrente e lo passa al gestore della diretta. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function aggiornaPunteggio() {
    try {
      const punteggio = await Api.get(scorePath || `/partite/${matchId}/punteggio`);
      if (onPunteggio) {
        onPunteggio(punteggio);
      }
    } catch {
    }
  }

  /**
   * Recupera i tiri successivi all’ultimo già visto e li notifica. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function verificaNuoviTiri() {
    if (!onTiro) {
      return;
    }
    try {
      let pagina = await Api.get(`/partite/${matchId}/tiri?size=100`);
      if (pagina.totalePagine > 1) {
        pagina = await Api.get(`/partite/${matchId}/tiri?size=100&page=${pagina.totalePagine - 1}`);
      }
      const tiri = pagina.contenuto;
      if (primoControlloTiri) {
        primoControlloTiri = false;
        ultimoTiroIdVisto = tiri.length ? tiri[tiri.length - 1].id : 0;
        return;
      }
      if (!tiri.length) {
        return;
      }
      const nuovi = tiri.filter((t) => t.id > ultimoTiroIdVisto);
      for (const tiro of nuovi) {
        onTiro({
          matchId,
          tipo: tiro.tipo,
          settore: tiro.settore,
          moltiplicatore: tiro.moltiplicatore,
          teamId: tiro.teamId,
          punti: tiro.punti,
          esito: tiro.esito,
        });
      }
      if (nuovi.length) {
        ultimoTiroIdVisto = tiri[tiri.length - 1].id;
      }
    } catch {
    }
  }

  /**
   * Avvia il ciclo di lettura periodica di tiri e punteggio. 
   */
  function avviaPolling() {
    if (fermato) {
      return;
    }
    impostaModalita("polling");
    /**
     * Legge tiri e punteggio e pianifica il prossimo aggiornamento.
     *
     * @returns {Promise<void>} Completamento dell’operazione.
     * 
     */
    const ciclo = async () => {
      if (fermato) {
        return;
      }
      await verificaNuoviTiri();
      await aggiornaPunteggio();
      timerPolling = setTimeout(ciclo, INTERVALLO_POLLING_MS);
    };
    ciclo();
  }

  /**
   * Apre la connessione MQTT e ripiega sul polling se non disponibile. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  async function avviaMqtt() {
    if (typeof Paho === "undefined") {
      avviaPolling();
      return;
    }
    let mqttUrl = "auto";
    try {
      const response = await fetch("/client-config.json", { cache: "no-store" });
      if (response.ok) mqttUrl = (await response.json()).mqttWebSocketUrl ?? "auto";
    } catch {   }
    if (fermato) return;
    if (!mqttUrl) { avviaPolling(); return; }
    try {
      if (mqttUrl === "auto") {
        client = new Paho.Client(location.hostname, PORTA_WEBSOCKET, `webapp-${matchId}-${Date.now()}`);
      } else {
        const url = new URL(mqttUrl, location.href);
        if (!["ws:", "wss:"].includes(url.protocol) || (location.protocol === "https:" && url.protocol !== "wss:"))
          throw new Error("URL MQTT WebSocket non valido");
        client = new Paho.Client(url.href, `webapp-${matchId}-${Date.now()}`);
      }
    } catch {
      avviaPolling();
      return;
    }

    /**
     * Avvia il polling quando si interrompe la connessione MQTT.
     *
     * 
     */
    client.onConnectionLost = () => {
      if (!fermato) {
        if (timerPunteggio) clearTimeout(timerPunteggio);
        avviaPolling();
      }
    };
    /**
     * Elabora gli eventi MQTT della partita corrente e aggiorna il punteggio.
     *
     * @param {Object} message Messaggio MQTT ricevuto dal client.
     * 
     */
    client.onMessageArrived = (message) => {
      let evento;
      try {
        evento = JSON.parse(message.payloadString);
      } catch {
        return;
      }
      if (Number(evento.matchId) !== Number(matchId)) {
        return;
      }
      if (onTiro) {
        onTiro(evento);
      }
      aggiornaPunteggio();
    };

    client.connect({
      useSSL: mqttUrl === "auto" ? location.protocol === "https:" : new URL(mqttUrl, location.href).protocol === "wss:",
      timeout: 4,
      /**
       * Sottoscrive gli eventi della partita e avvia il controllo del punteggio.
       *
       * 
       */
      onSuccess: () => {
        if (fermato) {
          return;
        }
        impostaModalita("mqtt");
        client.subscribe(TOPIC_EVENTO, { qos: 0 });
        aggiornaPunteggio();
        /**
         * Aggiorna periodicamente il punteggio mentre la connessione MQTT è attiva.
         *
         * @returns {Promise<void>} Completamento dell’operazione.
         * 
         */
        const verificaPunteggio = async () => {
          if (fermato) return;
          await aggiornaPunteggio();
          timerPunteggio = setTimeout(verificaPunteggio, INTERVALLO_POLLING_MS);
        };
        timerPunteggio = setTimeout(verificaPunteggio, INTERVALLO_POLLING_MS);
      },
      /**
       * Avvia il polling dopo un errore di connessione MQTT.
       *
       * @returns {void} Nessun valore restituito.
       * 
       */
      onFailure: () => avviaPolling(),
    });
  }

  avviaMqtt();

  return {
    /**
     * Interrompe gli aggiornamenti e chiude la connessione MQTT.
     *
     * 
     */
    ferma() {
      fermato = true;
      if (timerPolling) {
        clearTimeout(timerPolling);
      }
      if (timerPunteggio) {
        clearTimeout(timerPunteggio);
      }
      if (client) {
        try {
          if (client.isConnected()) {
            client.disconnect();
          }
        } catch {
        }
      }
    },
  };
}
