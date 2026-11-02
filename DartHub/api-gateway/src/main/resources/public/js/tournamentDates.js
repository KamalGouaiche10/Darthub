/**
 * Formatta un istante nel formato italiano di data e ora.
 *
 * @param {string} value Data e ora da convertire.
 * @returns {string} Data formattata, oppure il messaggio di indisponibilità.
 * 
 */
export function dataOraLocale(value) {
  return value ? new Date(value).toLocaleString("it-IT", { dateStyle: "medium", timeStyle: "short" }) : "Non disponibile";
}

/**
 * Converte il valore del campo data e ora in un istante ISO.
 *
 * @param {string} value Data e ora da convertire.
 * @returns {string} Istante nel formato ISO 8601.
 * @throws {Error} Se il campo non contiene una data valida.
 * 
 */
export function istanteDaCampo(value) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) throw new Error("Inserisci data e ora valide.");
  return date.toISOString();
}

/**
 * Verifica lo stato del torneo e che la data di inizio non sia trascorsa.
 *
 * @param {Object} torneo Stato e data di inizio del torneo.
 * @returns {boolean} True se le iscrizioni sono ancora aperte.
 * 
 */
export function iscrizioniAperte(torneo) {
  return torneo.stato === "APERTO" && Date.now() < new Date(torneo.dataInizio).getTime();
}

/**
 * Verifica se lo stato o l’esito indicano un abbinamento terminato.
 *
 * @param {Object} abbinamento Stato ed esito dell’abbinamento.
 * @returns {boolean} True se l’abbinamento è concluso.
 * 
 */
export function abbinamentoConcluso(abbinamento) {
  return ["COMPLETATA", "SINCRONIZZATA", "ANNULLATA"].includes(abbinamento.stato)
    || ["COMPLETATA", "VITTORIA_A_TAVOLINO", "DOPPIA_ASSENZA"].includes(abbinamento.outcome);
}
