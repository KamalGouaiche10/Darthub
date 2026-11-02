
const NS = "http://www.w3.org/2000/svg";
const SETTORI_ORDINE = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
const CENTRO = 210;
const RAGGI = { innerBull: 8, outerBull: 16, innerSingleFine: 99, tripleFine: 107, outerSingleFine: 170, doubleFine: 200 };

/**
 * Estrae un punto distribuito uniformemente sull’area utile del bersaglio.
 *
 * @returns {{x: number, y: number}} Coordinate del punto estratto.
 * 
 */
function puntoCasuale() {
  const angolo = Math.random() * Math.PI * 2;
  const raggio = Math.sqrt(Math.random()) * (RAGGI.doubleFine - 0.5);
  return { x: CENTRO + raggio * Math.cos(angolo), y: CENTRO + raggio * Math.sin(angolo) };
}

/**
 * Individua settore e moltiplicatore usando la geometria del bersaglio.
 *
 * @param {number} x Coordinata orizzontale nel sistema SVG del bersaglio.
 * @param {number} y Coordinata verticale nel sistema SVG del bersaglio.
 * @returns {?{settore: string, moltiplicatore: string}} Zona colpita, oppure null se il punto è esterno al bersaglio.
 * @see creaBersaglio
 * 
 */
export function zonaDaPunto(x, y) {
  const dx = x - CENTRO;
  const dy = y - CENTRO;
  const raggio = Math.hypot(dx, dy);
  if (raggio > RAGGI.doubleFine) return null;
  if (raggio <= RAGGI.innerBull) return { settore: "BULL", moltiplicatore: "INNER_BULL" };
  if (raggio <= RAGGI.outerBull) return { settore: "BULL", moltiplicatore: "OUTER_BULL" };

  const angolo = (Math.atan2(dy, dx) * 180 / Math.PI + 90 + 360) % 360;
  const indice = Math.floor((angolo + 9) / 18) % SETTORI_ORDINE.length;
  const settore = `S${SETTORI_ORDINE[indice]}`;
  const moltiplicatore = raggio < RAGGI.innerSingleFine ? "SINGLE"
    : raggio < RAGGI.tripleFine ? "TRIPLE"
      : raggio < RAGGI.outerSingleFine ? "SINGLE" : "DOUBLE";
  return { settore, moltiplicatore };
}

/**
 * Crea il gruppo SVG della freccetta e lo aggiunge al bersaglio.
 *
 * @param {SVGSVGElement} svg Bersaglio a cui aggiungere la freccetta.
 * @returns {SVGGElement} Gruppo grafico della freccetta.
 * 
 */
function creaFreccetta(svg) {
  const gruppo = document.createElementNS(NS, "g");
  gruppo.setAttribute("class", "freccetta-impatto");
  gruppo.setAttribute("aria-hidden", "true");
  gruppo.setAttribute("pointer-events", "none");
  const asta = document.createElementNS(NS, "path");
  asta.setAttribute("d", "M 0 0 L 39 39");
  asta.setAttribute("fill", "none");
  asta.setAttribute("stroke", "#edf8dc");
  asta.setAttribute("stroke-width", "4");
  asta.setAttribute("stroke-linecap", "round");
  const punta = document.createElementNS(NS, "path");
  punta.setAttribute("d", "M 0 0 L 8 3 L 3 8 Z");
  punta.setAttribute("fill", "#d7e6c5");
  const alette = document.createElementNS(NS, "path");
  alette.setAttribute("d", "M 32 38 L 31 21 L 48 30 L 51 51 L 30 48 L 21 31 Z");
  alette.setAttribute("fill", "#c7f36b");
  alette.setAttribute("stroke", "#25391f");
  alette.setAttribute("stroke-width", "1.5");
  gruppo.append(asta, punta, alette);
  svg.appendChild(gruppo);
  return gruppo;
}

/**
 * Anima la freccetta verso il punto, rispettando la preferenza di movimento ridotto.
 *
 * @param {SVGGElement} gruppo Gruppo SVG della freccetta.
 * @param {{x: number, y: number}} punto Punto di arrivo della freccetta.
 * @returns {Promise<void>} Completamento dell’animazione.
 * 
 */
function animaVolo(gruppo, punto) {
  const durata = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 520;
  if (!durata) {
    gruppo.setAttribute("transform", `translate(${punto.x} ${punto.y})`);
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const inizio = performance.now();
    /**
     * Aggiorna posizione, scala e opacità della freccetta nel fotogramma corrente.
     *
     * @param {number} ora Istante del fotogramma in millisecondi.
     * 
     */
    const fotogramma = (ora) => {
      const avanzamento = Math.min(1, (ora - inizio) / durata);
      const effetto = 1 - (1 - avanzamento) ** 3;
      const x = punto.x + (1 - effetto) * 145;
      const y = punto.y + (1 - effetto) * 175;
      const scala = 1 + (1 - effetto) * 0.45;
      gruppo.setAttribute("transform", `translate(${x} ${y}) scale(${scala})`);
      gruppo.style.opacity = String(Math.min(1, avanzamento * 5));
      if (avanzamento < 1) requestAnimationFrame(fotogramma);
      else resolve();
    };
    requestAnimationFrame(fotogramma);
  });
}

/**
 * Converte raggio e angolo nelle coordinate del bersaglio.
 *
 * @param {number} raggio Raggio rispetto al centro del bersaglio.
 * @param {number} angoloDeg Angolo in gradi, con zero verso l’alto.
 * @returns {Array<number>} Coordinate X e Y.
 * 
 */
function puntoPolare(raggio, angoloDeg) {
  const rad = ((angoloDeg - 90) * Math.PI) / 180;
  return [CENTRO + raggio * Math.cos(rad), CENTRO + raggio * Math.sin(rad)];
}

/**
 * Calcola il tracciato SVG di un settore anulare.
 *
 * @param {number} raggioInterno Raggio interno della zona.
 * @param {number} raggioEsterno Raggio esterno della zona.
 * @param {number} angoloIniziale Angolo iniziale in gradi.
 * @param {number} angoloFinale Angolo finale in gradi.
 * @returns {string} Tracciato da usare nell’attributo d.
 * @see puntoPolare
 * 
 */
function arcoPath(raggioInterno, raggioEsterno, angoloIniziale, angoloFinale) {
  const [x1, y1] = puntoPolare(raggioEsterno, angoloIniziale);
  const [x2, y2] = puntoPolare(raggioEsterno, angoloFinale);
  const [x3, y3] = puntoPolare(raggioInterno, angoloFinale);
  const [x4, y4] = puntoPolare(raggioInterno, angoloIniziale);
  return `M ${x1} ${y1} A ${raggioEsterno} ${raggioEsterno} 0 0 1 ${x2} ${y2} L ${x3} ${y3} A ${raggioInterno} ${raggioInterno} 0 0 0 ${x4} ${y4} Z`;
}

/**
 * Collega mouse e tastiera alla selezione di una zona del bersaglio.
 *
 * @param {SVGElement} elemento Zona SVG da rendere selezionabile.
 * @param {string} settore Codice del settore colpito.
 * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
 * @param {Function} onClick Gestore della selezione della zona.
 * 
 */
function collegaInterazione(elemento, settore, moltiplicatore, onClick) {
  const nomi = { SINGLE: "singolo", DOUBLE: "doppio", TRIPLE: "triplo", OUTER_BULL: "bull esterno", INNER_BULL: "bull centrale" };
  elemento.dataset.settore = settore;
  elemento.dataset.moltiplicatore = moltiplicatore;
  elemento.setAttribute("role", "button");
  elemento.setAttribute("tabindex", "0");
  elemento.setAttribute("aria-label", `${settore === "BULL" ? "Bull" : "Settore " + settore.slice(1)}, ${nomi[moltiplicatore]}`);
  elemento.style.cursor = "pointer";
  elemento.addEventListener("click", () => onClick(settore, moltiplicatore));
  elemento.addEventListener("keydown", (evento) => {
    if (evento.key === "Enter" || evento.key === " ") {
      evento.preventDefault();
      onClick(settore, moltiplicatore);
    }
  });
}

/**
 * Crea una zona SVG del bersaglio e collega la selezione del tiro.
 *
 * @param {string} d Tracciato SVG della zona.
 * @param {string} settore Codice del settore colpito.
 * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
 * @param {string} colore Colore di riempimento della zona.
 * @param {Function} onClick Gestore della selezione della zona.
 * @returns {SVGPathElement} Zona del bersaglio.
 * 
 */
function creaPath(d, settore, moltiplicatore, colore, onClick) {
  const path = document.createElementNS(NS, "path");
  path.setAttribute("d", d);
  path.setAttribute("fill", colore);
  path.setAttribute("stroke", "#0d1b1c");
  path.setAttribute("stroke-width", "0.6");
  collegaInterazione(path, settore, moltiplicatore, onClick);
  return path;
}

/**
 * Disegna il bersaglio nel contenitore e prepara le interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {Function} onTiro Gestore richiamato con settore e moltiplicatore del tiro.
 * @returns {Object} Comandi per interagire con il bersaglio.
 * 
 */
export function creaBersaglio(container, onTiro) {
  let zonaSelezionata = { settore: "S20", moltiplicatore: "SINGLE" };
  let freccetta = null;
  let lancioInCorso = false;
  /**
   * Seleziona una zona e invia il tiro se non è in corso un lancio. @param {string} settore Codice del settore colpito.
   * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
   * 
   */
  const seleziona = (settore, moltiplicatore) => {
    if (lancioInCorso) return;
    zonaSelezionata = { settore, moltiplicatore };
    onTiro(settore, moltiplicatore);
  };
  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("viewBox", `-12 -12 ${CENTRO * 2 + 24} ${CENTRO * 2 + 24}`);
  svg.setAttribute("class", "bersaglio-svg");
  svg.setAttribute("role", "group");
  svg.setAttribute("aria-label", "Bersaglio interattivo da freccette");

  const bordo = document.createElementNS(NS, "circle");
  bordo.setAttribute("cx", CENTRO);
  bordo.setAttribute("cy", CENTRO);
  bordo.setAttribute("r", "204");
  bordo.setAttribute("fill", "#13201e");
  bordo.setAttribute("stroke", "#68876b");
  bordo.setAttribute("stroke-width", "6");
  svg.appendChild(bordo);

  const ampiezza = 360 / SETTORI_ORDINE.length;

  SETTORI_ORDINE.forEach((numero, indice) => {
    const angoloIniziale = indice * ampiezza - ampiezza / 2;
    const angoloFinale = angoloIniziale + ampiezza;
    const pari = indice % 2 === 0;
    const settore = `S${numero}`;

    const zone = [
      { r1: RAGGI.outerBull, r2: RAGGI.innerSingleFine, moltiplicatore: "SINGLE", colore: pari ? "#e3e9d6" : "#1b2b29" },
      { r1: RAGGI.innerSingleFine, r2: RAGGI.tripleFine, moltiplicatore: "TRIPLE", colore: pari ? "#45a36e" : "#bb4d50" },
      { r1: RAGGI.tripleFine, r2: RAGGI.outerSingleFine, moltiplicatore: "SINGLE", colore: pari ? "#e3e9d6" : "#1b2b29" },
      { r1: RAGGI.outerSingleFine, r2: RAGGI.doubleFine, moltiplicatore: "DOUBLE", colore: pari ? "#45a36e" : "#bb4d50" },
    ];
    for (const zona of zone) {
      svg.appendChild(creaPath(arcoPath(zona.r1, zona.r2, angoloIniziale, angoloFinale), settore, zona.moltiplicatore, zona.colore, seleziona));
    }

    const [xLabel, yLabel] = puntoPolare(RAGGI.doubleFine + 12, indice * ampiezza);
    const etichetta = document.createElementNS(NS, "text");
    etichetta.setAttribute("x", xLabel);
    etichetta.setAttribute("y", yLabel);
    etichetta.setAttribute("text-anchor", "middle");
    etichetta.setAttribute("dominant-baseline", "middle");
    etichetta.setAttribute("fill", "#e8f1e8");
    etichetta.setAttribute("font-size", "11");
    etichetta.setAttribute("pointer-events", "none");
    etichetta.textContent = String(numero);
    svg.appendChild(etichetta);
  });

  const cerchioOuterBull = document.createElementNS(NS, "circle");
  cerchioOuterBull.setAttribute("cx", CENTRO);
  cerchioOuterBull.setAttribute("cy", CENTRO);
  cerchioOuterBull.setAttribute("r", RAGGI.outerBull);
  cerchioOuterBull.setAttribute("fill", "#45a36e");
  collegaInterazione(cerchioOuterBull, "BULL", "OUTER_BULL", seleziona);
  svg.appendChild(cerchioOuterBull);

  const cerchioInnerBull = document.createElementNS(NS, "circle");
  cerchioInnerBull.setAttribute("cx", CENTRO);
  cerchioInnerBull.setAttribute("cy", CENTRO);
  cerchioInnerBull.setAttribute("r", RAGGI.innerBull);
  cerchioInnerBull.setAttribute("fill", "#bb4d50");
  collegaInterazione(cerchioInnerBull, "BULL", "INNER_BULL", seleziona);
  svg.appendChild(cerchioInnerBull);

  container.appendChild(svg);

  return {
    /**
     * Anima un tiro casuale e lo invia al simulatore.
     *
     * @returns {Promise<boolean>} True se il tiro è stato inviato, false se occupato o rifiutato.
     * @throws {*} Se l’animazione o l’invio del tiro falliscono.
     * 
     */
    async lanciaCasuale() {
      if (lancioInCorso) return false;
      lancioInCorso = true;
      freccetta?.remove();
      const punto = puntoCasuale();
      const zona = zonaDaPunto(punto.x, punto.y);
      freccetta = creaFreccetta(svg);
      try {
        await animaVolo(freccetta, punto);
        const inviato = await onTiro(zona.settore, zona.moltiplicatore);
        if (inviato) zonaSelezionata = zona;
        else freccetta.remove();
        return Boolean(inviato);
      } catch (errore) {
        freccetta.remove();
        throw errore;
      } finally {
        lancioInCorso = false;
      }
    },
    /**
     * Invia un tiro sulla zona selezionata se non è in corso un lancio.
     *
     * 
     */
    tiraSelezionato() {
      if (lancioInCorso) return;
      onTiro(zonaSelezionata.settore, zonaSelezionata.moltiplicatore);
    },
    /**
     * Evidenzia temporaneamente la zona colpita del bersaglio.
     *
     * @param {string} settore Codice del settore colpito.
     * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
     * 
     */
    evidenzia(settore, moltiplicatore) {
      const zone = svg.querySelectorAll(`[data-settore="${settore}"][data-moltiplicatore="${moltiplicatore}"]`);
      zone.forEach((zona) => {
        zona.classList.add("zona-colpita");
        setTimeout(() => zona.classList.remove("zona-colpita"), 1000);
      });
    },
  };
}
