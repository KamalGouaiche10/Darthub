
const NS = "http://www.w3.org/2000/svg";
const SETTORI_ORDINE = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
const CENTRO = 210;
const RAGGI = { innerBull: 8, outerBull: 16, innerSingleFine: 99, tripleFine: 107, outerSingleFine: 170, doubleFine: 200 };

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
 * Crea una zona SVG del bersaglio e collega la selezione del tiro.
 *
 * @param {string} d Tracciato SVG della zona.
 * @param {string} settore Codice del settore colpito.
 * @param {string} moltiplicatore Tipo di zona: singolo, doppio, triplo o bull.
 * @param {string} colore Colore di riempimento della zona.
 * @param {Function} onTiro Gestore richiamato con settore e moltiplicatore del tiro.
 * @returns {SVGPathElement} Zona del bersaglio.
 * 
 */
function creaPath(d, settore, moltiplicatore, colore, onTiro) {
  const path = document.createElementNS(NS, "path");
  path.setAttribute("d", d);
  path.setAttribute("fill", colore);
  path.setAttribute("stroke", "#0b1020");
  path.setAttribute("stroke-width", "0.6");
  path.dataset.settore = settore;
  path.dataset.moltiplicatore = moltiplicatore;
  if (onTiro) path.addEventListener("click", () => onTiro(settore, moltiplicatore));
  return path;
}

/**
 * Disegna il bersaglio nel contenitore e prepara le interazioni.
 *
 * @param {HTMLElement} container Contenitore in cui mostrare il contenuto.
 * @param {?Function} [onTiro] Gestore richiamato con settore e moltiplicatore del tiro.
 * @returns {Object} Comandi per interagire con il bersaglio.
 * 
 */
export function creaBersaglio(container, onTiro = null) {
  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("viewBox", `0 0 ${CENTRO * 2} ${CENTRO * 2}`);
  svg.setAttribute("class", "bersaglio-svg");

  const ampiezza = 360 / SETTORI_ORDINE.length;

  SETTORI_ORDINE.forEach((numero, indice) => {
    const angoloIniziale = indice * ampiezza - ampiezza / 2;
    const angoloFinale = angoloIniziale + ampiezza;
    const pari = indice % 2 === 0;
    const settore = `S${numero}`;

    const zone = [
      { r1: RAGGI.outerBull, r2: RAGGI.innerSingleFine, moltiplicatore: "SINGLE", colore: pari ? "#e8e2c9" : "#111827" },
      { r1: RAGGI.innerSingleFine, r2: RAGGI.tripleFine, moltiplicatore: "TRIPLE", colore: pari ? "#35c46d" : "#e64c4c" },
      { r1: RAGGI.tripleFine, r2: RAGGI.outerSingleFine, moltiplicatore: "SINGLE", colore: pari ? "#e8e2c9" : "#111827" },
      { r1: RAGGI.outerSingleFine, r2: RAGGI.doubleFine, moltiplicatore: "DOUBLE", colore: pari ? "#35c46d" : "#e64c4c" },
    ];
    for (const zona of zone) {
      svg.appendChild(creaPath(arcoPath(zona.r1, zona.r2, angoloIniziale, angoloFinale), settore, zona.moltiplicatore, zona.colore, onTiro));
    }

    const [xLabel, yLabel] = puntoPolare(RAGGI.doubleFine + 12, indice * ampiezza);
    const etichetta = document.createElementNS(NS, "text");
    etichetta.setAttribute("x", xLabel);
    etichetta.setAttribute("y", yLabel);
    etichetta.setAttribute("text-anchor", "middle");
    etichetta.setAttribute("dominant-baseline", "middle");
    etichetta.setAttribute("fill", "#f0f2f8");
    etichetta.setAttribute("font-size", "11");
    etichetta.textContent = String(numero);
    svg.appendChild(etichetta);
  });

  const cerchioOuterBull = document.createElementNS(NS, "circle");
  cerchioOuterBull.setAttribute("cx", CENTRO);
  cerchioOuterBull.setAttribute("cy", CENTRO);
  cerchioOuterBull.setAttribute("r", RAGGI.outerBull);
  cerchioOuterBull.setAttribute("fill", "#35c46d");
  cerchioOuterBull.dataset.settore = "BULL";
  cerchioOuterBull.dataset.moltiplicatore = "OUTER_BULL";
  if (onTiro) cerchioOuterBull.addEventListener("click", () => onTiro("BULL", "OUTER_BULL"));
  svg.appendChild(cerchioOuterBull);

  const cerchioInnerBull = document.createElementNS(NS, "circle");
  cerchioInnerBull.setAttribute("cx", CENTRO);
  cerchioInnerBull.setAttribute("cy", CENTRO);
  cerchioInnerBull.setAttribute("r", RAGGI.innerBull);
  cerchioInnerBull.setAttribute("fill", "#e64c4c");
  cerchioInnerBull.dataset.settore = "BULL";
  cerchioInnerBull.dataset.moltiplicatore = "INNER_BULL";
  if (onTiro) cerchioInnerBull.addEventListener("click", () => onTiro("BULL", "INNER_BULL"));
  svg.appendChild(cerchioInnerBull);

  container.appendChild(svg);

  return {
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
