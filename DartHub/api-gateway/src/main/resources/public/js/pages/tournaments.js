import { Api, queryString } from "../api.js";
import { Auth } from "../auth.js";
import { notifica, notificaErrore } from "../notify.js";
import { renderStandings } from "../../components/standingsTable.js";
import { abbinamentoConcluso, dataOraLocale, iscrizioniAperte } from "../tournamentDates.js";

const CLASSE_STATO = { APERTO: "badge-ok", IN_CORSO: "badge-warn", CHIUSO: "badge-muted" };

/**
 * Prepara la pagina dei tornei e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<(Function|undefined)>} Funzione di chiusura degli aggiornamenti, oppure undefined se non avviati.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  container.innerHTML = `<div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> LA COMPETIZIONE</div><h1>Entra in torneo.</h1><p>Scopri le sfide aperte, iscriviti e segui la classifica.</p></div><section class="card"><h2>I miei abbinamenti</h2><div id="messaggio-bersaglio" role="status" hidden></div><div id="miei-abbinamenti">Caricamento…</div></section><div id="lista-tornei" class="tournament-grid grid gap-4"></div>`;
  const lista = container.querySelector("#lista-tornei");
  const miei = container.querySelector("#miei-abbinamenti");
  const messaggioBersaglio = container.querySelector("#messaggio-bersaglio");
  /**
   * Mostra il messaggio relativo al collegamento del bersaglio. @param {string} messaggio Messaggio da mostrare all’utente.
   * 
   */
  function mostraMessaggioBersaglio(messaggio) {
    messaggioBersaglio.textContent = messaggio;
    messaggioBersaglio.className = "stato-vuoto";
    messaggioBersaglio.hidden = false;
  }
  /**
   * Ricarica gli abbinamenti dell’utente e aggiorna le azioni disponibili. @returns {Promise<void>} Completamento dell’operazione.
   * 
   */
  const aggiornaAbbinamenti = async () => {
   try {
    const abbinamenti = await Api.get("/tornei/miei-abbinamenti");
    miei.replaceChildren();
    if (!abbinamenti.length) miei.textContent = "Nessun abbinamento pubblicato.";
    for (const a of abbinamenti) {
      const concluso = abbinamentoConcluso(a);
      const riga = document.createElement("div");
      riga.className = "card";
      const p = document.createElement("p");
      p.textContent = `${a.torneo} · turno ${a.turno} · ${a.incontro} · ${a.locale} · ${a.stato || "In attesa di orario"}`;
      riga.appendChild(p);
      if (a.individual && !a.scheduledAt && !a.riposo && !concluso) {
        const accepted = a.homeAccepted || a.awayAccepted;
        const stato = document.createElement("p");
        stato.textContent = accepted ? `Accettazioni: ${a.homeAccepted ? "sì" : "in attesa"} / ${a.awayAccepted ? "sì" : "in attesa"}.` : "Sfida in attesa di accettazione.";
        riga.appendChild(stato);
        const accept = document.createElement("button");
        accept.textContent = "Accetta la partita";
        if (a.acceptedByMe) {
          accept.textContent = a.homeAccepted && a.awayAccepted ? "Riprova assegnazione" : "Accettata";
          accept.disabled = !(a.homeAccepted && a.awayAccepted);
        }
        accept.addEventListener("click", async () => {
          accept.disabled = true;
          try {
            const response = await Api.post(`/tornei/partite/${a.matchId}/accetta`, {});
            notifica(response.appointment ? `Orario assegnato: ${dataOraLocale(response.appointment.scheduledAt)}` : "Accettazione registrata. In attesa dell'avversario.");
            await aggiornaAbbinamenti();
          } catch (e) { notificaErrore(e); accept.disabled = false; }
        });
        riga.appendChild(accept);
      }
      if (a.scheduledAt) {
        const info = document.createElement("p");
        info.textContent = concluso
          ? `Incontro concluso. Orario previsto: ${dataOraLocale(a.scheduledAt)}.`
          : `Partita: ${dataOraLocale(a.scheduledAt)}. Presentati entro ${dataOraLocale(a.deadline)}. Bersaglio disponibile dalle ${dataOraLocale(a.linkAt)}.`;
        riga.appendChild(info);
        const now = Date.now(), at = new Date(a.scheduledAt).getTime();
        if (!concluso && now < at) {
          const countdown = document.createElement("p");
          countdown.textContent = `Inizio tra ${Math.ceil((at - now) / 60000)} minuti.`;
          riga.appendChild(countdown);
        }
        if (a.outcome === "VITTORIA_A_TAVOLINO" || a.outcome === "DOPPIA_ASSENZA") {
          const outcome = document.createElement("p");
          outcome.textContent = a.esitoAssenza;
          riga.appendChild(outcome);
        } else if (!concluso) {
          const presence = document.createElement("p");
          presence.textContent = `Presenze: ${a.homeReady ? "pronto" : "assente"} / ${a.awayReady ? "pronto" : "assente"}.`;
          riga.appendChild(presence);
          if (a.linkAvailable && a.stato === "CREATA") {
            const ready = document.createElement("button");
            ready.textContent = "Sono pronto";
            ready.addEventListener("click", async () => {
              try { await Api.post(`/tornei/partite/${a.matchId}/pronto`, {}); await aggiornaAbbinamenti(); }
              catch (e) { notificaErrore(e); }
            });
            riga.appendChild(ready);
          }
          if (a.individual) {
            const apri = document.createElement("button");
            apri.type = "button";
            apri.textContent = "Apri il bersaglio";
            apri.addEventListener("click", async () => {
              if (!Number.isSafeInteger(Number(a.matchId)) || Number(a.matchId) <= 0) {
                mostraMessaggioBersaglio("La partita del torneo non è ancora disponibile. Riprova più tardi.");
                return;
              }
              apri.disabled = true;
              try {
                const accesso = await Api.get(`/tornei/partite/${a.matchId}/accesso`);
                if (Number(accesso.matchId) !== Number(a.matchId)) {
                  throw new Error("I dati del bersaglio non corrispondono alla partita. Riprova più tardi.");
                }
                location.hash = `#/partite/${a.matchId}`;
              } catch (e) {
                mostraMessaggioBersaglio(e.status === 403 && a.linkAt && Date.now() < new Date(a.linkAt).getTime()
                  ? `Il bersaglio della partita sarà disponibile dalle ${dataOraLocale(a.linkAt)}.`
                  : e.status === 404
                    ? "Il bersaglio di questa partita non è ancora disponibile. Riprova più tardi."
                    : e.message);
                apri.disabled = false;
              }
            });
            riga.appendChild(apri);
          }
        }
      } else if (concluso && !a.riposo) {
        const info = document.createElement("p");
        info.textContent = a.stato === "ANNULLATA" ? "Partita annullata." : "Incontro concluso.";
        riga.appendChild(info);
      }
      miei.appendChild(riga);
    }
   } catch (e) {
    miei.textContent = e.message;
   }
  };
  await aggiornaAbbinamenti();
  const refresh = setInterval(aggiornaAbbinamenti, 5000);

  let tornei, mieIscrizioni;
  try {
    [tornei, mieIscrizioni] = await Promise.all([
      Api.get("/tornei" + queryString({ size: 50 })),
      Api.get("/tornei/mie-iscrizioni"),
    ]);
  } catch (e) {
    lista.innerHTML = `<div class="stato-errore">${e.message}</div>`;
    return () => clearInterval(refresh);
  }
  if (!tornei.contenuto.length) {
    lista.innerHTML = '<div class="stato-vuoto empty-panel">Nessun torneo disponibile al momento.</div>';
    return () => clearInterval(refresh);
  }

  const iscritti = new Set(mieIscrizioni.map(Number));

  lista.innerHTML = tornei.contenuto
    .map(
      (t) => `
    <div class="card tournament-card transition-colors duration-200 hover:border-lime/40" data-torneo="${t.id}">
      <div class="tournament-top flex items-start justify-between"><span class="tournament-symbol" aria-hidden="true">✳</span><span class="badge ${CLASSE_STATO[t.stato] || "badge-muted"}">${t.stato}</span></div>
      <h2>${t.nome}</h2>
      <p>${t.modalita} · formula ${t.formula} · ${t.locali.length} locali</p>
      <p>Inizio: ${dataOraLocale(t.dataInizio)} · Fine: ${dataOraLocale(t.dataFine)}</p>
      ${t.calendarioEsito === "ISCRITTI_INSUFFICIENTI" ? "<p>Iscritti insufficienti: nessun abbinamento disponibile.</p>" : ""}
      <div class="tournament-actions">
        ${t.modalita === "INDIVIDUALE" && iscritti.has(Number(t.id))
          ? '<span class="badge badge-ok" data-stato-iscrizione>Già iscritto</span>'
          : t.modalita === "INDIVIDUALE" && iscrizioniAperte(t)
            ? `<button class="secondario" data-iscriviti="${t.id}">Iscriviti</button>` : ""}
        <button class="secondario" data-classifica="${t.id}">Classifica</button>
      </div>
      <div id="classifica-${t.id}" style="display: none;"></div>
    </div>`,
    )
    .join("");

  lista.querySelectorAll("[data-iscriviti]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      btn.disabled = true;
      try {
        await Api.post(`/tornei/${btn.dataset.iscriviti}/iscrizioni`, { participantId: user.userId });
        notifica("Sei iscritto. Torna dal giorno di inizio del torneo per vedere il tuo avversario.");
        btn.outerHTML = '<span class="badge badge-ok" data-stato-iscrizione>Già iscritto</span>';
      } catch (e) {
        if (e.codice === "GIA_ISCRITTO") {
          btn.outerHTML = '<span class="badge badge-ok" data-stato-iscrizione>Già iscritto</span>';
        } else {
          notificaErrore(e);
        }
      } finally {
        btn.disabled = false;
      }
    });
  });

  lista.querySelectorAll("[data-classifica]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.classifica;
      const zona = document.getElementById(`classifica-${id}`);
      const visibile = zona.style.display !== "none";
      if (visibile) {
        zona.style.display = "none";
        return;
      }
      zona.style.display = "";
      zona.innerHTML = '<div class="stato-caricamento">Caricamento…</div>';
      try {
        const classifica = await Api.get(`/tornei/${id}/classifica`);
        renderStandings(zona, classifica);
      } catch (e) {
        zona.innerHTML = `<div class="stato-errore">${e.message}</div>`;
      }
    });
  });
  return () => clearInterval(refresh);
}
