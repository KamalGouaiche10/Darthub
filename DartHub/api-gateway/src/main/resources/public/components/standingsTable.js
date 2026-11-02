import { escapeHtml } from "../js/html.js";

/**
 * Mostra le righe della classifica nel contenitore.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Array<Object>} righe Righe da visualizzare.
 * 
 */
export function renderStandings(container, righe) {
  const corpo = righe.length
    ? righe
        .map(
          (r) => `<tr>
        <td>${r.posizione ?? "-"}</td>
        <td>${escapeHtml(r.participantName || "Partecipante non disponibile")}</td>
        <td>${r.punti}</td>
        <td>${r.vittorie}</td>
        <td>${r.partiteGiocate}</td>
      </tr>`,
        )
        .join("")
    : '<tr><td colspan="5" class="stato-vuoto">Nessuna partita ancora giocata</td></tr>';
  container.innerHTML = `<table>
    <thead><tr><th>Pos.</th><th>Partecipante</th><th>Punti</th><th>Vittorie</th><th>Partite</th></tr></thead>
    <tbody>${corpo}</tbody>
  </table>`;
}
