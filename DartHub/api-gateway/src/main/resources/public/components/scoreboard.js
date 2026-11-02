import { escapeHtml as testoSicuro } from "../js/html.js";

import { Auth } from "../js/auth.js";

/**
 * Mostra stato, squadre e punteggi della partita.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Object} scoreDto Stato e punteggi della partita.
 * 
 */
export function renderScoreboard(container, scoreDto) {
  const user = Auth.currentUser();
  const righe = [...scoreDto.punteggi]
    .sort((a, b) => a.ordineTurno - b.ordineTurno)
    .map((p) => {
      let nome;
      if (p.playerId != null) {
        const base = testoSicuro(p.nomeGiocatore
          || (user && p.playerId === user.userId ? user.nickname || user.username : null)
          || `Giocatore #${p.playerId}`);
        nome = user && p.playerId === user.userId ? `${base} (tu)` : base;
      } else if (p.teamId != null) {
        nome = p.nomeSquadra ? testoSicuro(p.nomeSquadra) : `Squadra #${p.teamId}`;
      } else {
        nome = "Ospite";
      }
      const attivo = p.participantId === scoreDto.partecipanteAttivoId;
      return `<tr class="${attivo ? "punteggio-attivo" : ""}">
        <td>${nome}${p.ritirato ? ' <span class="badge badge-muted">ritirato</span>' : ""}</td>
        <td style="text-align:right; font-size:1.4rem; font-weight:700;">${p.punteggioCorrente}</td>
      </tr>`;
    })
    .join("");
  container.innerHTML = `<table><tbody>${righe}</tbody></table>`;
}
