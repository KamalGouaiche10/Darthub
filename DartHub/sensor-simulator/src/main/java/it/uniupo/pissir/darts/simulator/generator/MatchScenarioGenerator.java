package it.uniupo.pissir.darts.simulator.generator;

import java.util.Optional;
import java.util.Random;

/**
 * Genera una sequenza di tiri per simulare una partita.
 *
 * @see java.util.Random
 *  
 */
public final class MatchScenarioGenerator {

    private static final int PUNTEGGIO_INIZIALE = 501;
    private static final int TIRI_PER_TURNO = 3;
    private static final int SOGLIA_TIRO_MIRATO = 60;
    private static final double PROBABILITA_TENTATIVO_MIRATO = 0.6;

    private final Random random;
    private final ThrowEventGenerator generatoreCasuale;
    private final int[] punteggi = {PUNTEGGIO_INIZIALE, PUNTEGGIO_INIZIALE};
    private int giocatoreAttivo;
    private int tiriNelTurno;
    private int puntiInizioTurno = PUNTEGGIO_INIZIALE;
    private boolean partitaFinita;

    /**
     * Inizializza {@code MatchScenarioGenerator} con i dati e le dipendenze indicati.
     *
     * @param seed seme del generatore casuale
     *  
     */
    public MatchScenarioGenerator(Long seed) {
        this.random = seed != null ? new Random(seed) : new Random();
        this.generatoreCasuale = new ThrowEventGenerator(seed);
    }

    /**
     * Genera il tiro successivo dello scenario.
     *
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<GeneratedThrow> prossimoTiro() {
        if (partitaFinita) {
            return Optional.empty();
        }
        int residuo = punteggi[giocatoreAttivo];
        GeneratedThrow tiro = residuo <= SOGLIA_TIRO_MIRATO && random.nextDouble() < PROBABILITA_TENTATIVO_MIRATO
                ? tiroMirato(residuo)
                : generatoreCasuale.generate();
        applica(tiro);
        return Optional.of(tiro);
    }

    /**
     * Aggiorna lo scenario applicando il tiro generato.
     *
     * @param tiro tiro da utilizzare
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    private void applica(GeneratedThrow tiro) {
        int residuo = punteggi[giocatoreAttivo];
        int nuovoResiduo = residuo - tiro.punti();

        boolean sforamento = nuovoResiduo < 0 || nuovoResiduo == 1;
        boolean vittoria = nuovoResiduo == 0 && tiro.isDouble();
        boolean bustSenzaDouble = nuovoResiduo == 0 && !tiro.isDouble();

        if (sforamento || bustSenzaDouble) {
            punteggi[giocatoreAttivo] = puntiInizioTurno;
            passaTurno();
            return;
        }
        if (vittoria) {
            punteggi[giocatoreAttivo] = 0;
            partitaFinita = true;
            return;
        }

        punteggi[giocatoreAttivo] = nuovoResiduo;
        tiriNelTurno++;
        if (tiriNelTurno >= TIRI_PER_TURNO) {
            passaTurno();
        }
    }

    /**
     * Seleziona il partecipante del turno successivo. 
     */
    private void passaTurno() {
        giocatoreAttivo = 1 - giocatoreAttivo;
        tiriNelTurno = 0;
        puntiInizioTurno = punteggi[giocatoreAttivo];
    }

    /**
     * Genera un tiro orientato alla chiusura della partita.
     *
     * @param residuo residuo da utilizzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.simulator.generator.GeneratedThrow} prodotto o recuperato
     * @see it.uniupo.pissir.darts.simulator.generator.GeneratedThrow
     *  
     */
    private GeneratedThrow tiroMirato(int residuo) {
        if (residuo == 50) {
            return new GeneratedThrow(DartSector.BULL, Multiplier.INNER_BULL);
        }
        if (residuo % 2 == 0 && residuo >= 2 && residuo <= 40) {
            DartSector settore = DartSector.SETTORI_NORMALI[residuo / 2 - 1];
            return new GeneratedThrow(settore, Multiplier.DOUBLE);
        }

        int base = Math.min(20, Math.max(1, (residuo - 2) / 2));
        DartSector settore = DartSector.SETTORI_NORMALI[base - 1];
        return new GeneratedThrow(settore, Multiplier.SINGLE);
    }
}
