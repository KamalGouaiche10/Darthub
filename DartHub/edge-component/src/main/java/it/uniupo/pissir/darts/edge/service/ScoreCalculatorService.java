package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.edge.model.SensorEvent;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.game.model.darts.ScoreCalculator;

import java.time.Instant;

/**
 * Payload sensore → settore, moltiplicatore, punti. 
 */
public final class ScoreCalculatorService {

    /**
     * Raccoglie i dati di tiro interpretato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @param timestampSensore istante registrato dal sensore
     * @see it.uniupo.pissir.darts.game.model.darts.DartSector
     *  
     */
    public record TiroInterpretato(DartSector settore, Multiplier moltiplicatore, int punti, Instant timestampSensore) {
    }

    /**
     * Converte l'evento del sensore nei dati necessari al calcolo del tiro.
     *
     * @param evento evento da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.service.ScoreCalculatorService.TiroInterpretato} prodotto o recuperato
     * @see it.uniupo.pissir.darts.edge.service.ScoreCalculatorService.TiroInterpretato
     *  
     */
    public TiroInterpretato interpreta(SensorEvent evento) {
        DartSector settore = parseSettore(evento.settore());
        Multiplier moltiplicatore = parseMoltiplicatore(evento.moltiplicatore());
        int punti = ScoreCalculator.punti(settore, moltiplicatore).orElse(0);
        Instant timestamp = evento.timestamp() != null ? Instant.parse(evento.timestamp()) : Instant.now();
        return new TiroInterpretato(settore, moltiplicatore, punti, timestamp);
    }

    /**
     * Legge e converte settore dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.darts.DartSector} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.darts.DartSector
     *  
     */
    private DartSector parseSettore(String value) {
        try {
            return DartSector.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Settore non valido nel payload del sensore: " + value);
        }
    }

    /**
     * Legge e converte moltiplicatore dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.darts.Multiplier} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.darts.Multiplier
     *  
     */
    private Multiplier parseMoltiplicatore(String value) {
        try {
            return Multiplier.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Moltiplicatore non valido nel payload del sensore: " + value);
        }
    }
}
