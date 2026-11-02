import { Api, queryString } from "../api.js";
import { Auth } from "../auth.js";
import { Router } from "../router.js";
import { creaDataTable } from "../../components/dataTable.js";

const CLASSE_STATO = {
  CREATA: "badge-muted",
  IN_CORSO: "badge-warn",
  COMPLETATA: "badge-ok",
  SINCRONIZZATA: "badge-ok",
  ANNULLATA: "badge-danger",
};

/**
 * Prepara la pagina dello storico partite e collega caricamenti e interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @returns {Promise<void>} Completamento dell’operazione.
 * 
 */
export async function render(container) {
  const user = Auth.currentUser();
  container.innerHTML = `
    <div class="page-intro"><div class="eyebrow"><span class="eyebrow-dot"></span> IL TUO ARCHIVIO</div><h1>Le tue partite.</h1><p>Ogni sfida, ogni risultato. Ripercorri il tuo gioco.</p></div>
    <div class="card">
      <div class="riga-form">
        <div><label for="filtro-dal">Dal</label><input id="filtro-dal" type="date"></div>
        <div><label for="filtro-al">Al</label><input id="filtro-al" type="date"></div>
        <div><button type="button" id="btn-filtra" class="secondario">Filtra</button></div>
      </div>
    </div>
    <div class="card" id="tabella-partite"></div>`;

  const filtroDal = container.querySelector("#filtro-dal");
  const filtroAl = container.querySelector("#filtro-al");
  const tabellaContainer = container.querySelector("#tabella-partite");

  const tabella = creaDataTable(tabellaContainer, {
    colonne: [
      { chiave: "id", etichetta: "Numero" },
      { chiave: "dataInizio", etichetta: "Data",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) => (v ? new Date(v).toLocaleString() : "-") },
      { chiave: "modalita", etichetta: "Modalità" },
      { chiave: "stato", etichetta: "Stato",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) => `<span class="badge ${CLASSE_STATO[v] || "badge-muted"}">${v}</span>` },
      { chiave: "id", etichetta: "",
        /**
         * Formatta il valore della colonna per la visualizzazione nella tabella.
         *
         * @param {*} v Valore della cella.
         * @returns {string} Contenuto HTML della cella.
         * 
         */
        render: (v) => `<button class="secondario" data-vedi="${v}">Vedi</button>` },
    ],
    dimensionePagina: 100,
    /**
     * Carica una pagina di elementi per la tabella.
     *
     * @param {number} pagina Indice della pagina, a partire da zero.
     * @param {number} dimensione Numero di elementi della pagina.
     * @returns {Promise<Object>} Risultato paginato da visualizzare.
     * 
     */
    caricaPagina: (pagina, dimensione) =>
      Api.get(
        "/partite" +
          queryString({
            playerId: user.userId,
            page: pagina,
            size: dimensione,
            dal: filtroDal.value ? new Date(filtroDal.value).toISOString() : undefined,
            al: filtroAl.value ? new Date(filtroAl.value).toISOString() : undefined,
          }),
      ),
  });

  container.querySelector("#btn-filtra").addEventListener("click", () => tabella.ricarica());
  tabellaContainer.addEventListener("click", (evento) => {
    const id = evento.target.dataset.vedi;
    if (id) {
      Router.vaiA(`#/partite/${id}`);
    }
  });
}
