package it.uniupo.pissir.darts.game.model.darts;

import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.game.engine.MatchState;
import it.uniupo.pissir.darts.game.engine.RuleEngine;
import it.uniupo.pissir.darts.game.engine.RuleOutcome;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Applica le regole delle freccette allo stato della partita.
 *
 * @see it.uniupo.pissir.darts.game.engine.RuleEngine
 *  
 */
public final class DartsRuleEngine implements RuleEngine {

    private static final String CHIAVE_PUNTI_INIZIO_TURNO = "puntiInizioTurno";
    private static final String CHIAVE_TIRI_GIA_FATTI_NEL_TURNO = "tiriGiaFattiNelTurno";

    /**
     * Applica l'evento alla regola e allo stato corrente della partita.
     *
     * @param regola regola di punteggio da applicare
     * @param statoCorrente stato corrente
     * @param evento evento da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.engine.RuleOutcome} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.engine.RuleOutcome
     *  
     */
    @Override
    public RuleOutcome applica(ScoringRule regola, MatchState statoCorrente, GameEvent evento) {
        DartSector settore = DartSector.valueOf(String.valueOf(evento.payload().get("settore")));
        Multiplier moltiplicatore = Multiplier.valueOf(String.valueOf(evento.payload().get("moltiplicatore")));
        int punteggioCorrente = statoCorrente.punteggioDi(evento.participantId());
        int puntiInizioTurno = numeroExtra(statoCorrente, CHIAVE_PUNTI_INIZIO_TURNO, punteggioCorrente);
        int tiriGiaFattiNelTurno = numeroExtra(statoCorrente, CHIAVE_TIRI_GIA_FATTI_NEL_TURNO, 0);

        ThrowOutcome esito = valuta(regola, punteggioCorrente, puntiInizioTurno, tiriGiaFattiNelTurno, settore, moltiplicatore);

        Map<Long, Integer> nuoviPunteggi = new HashMap<>(statoCorrente.punteggiPerPartecipante());
        nuoviPunteggi.put(evento.participantId(), esito.punteggioResiduo());
        Map<String, Object> nuoviExtra = new HashMap<>(statoCorrente.extra());
        if (esito.esito() == it.uniupo.pissir.darts.game.model.darts.Esito.VALIDO && !esito.turnoAnnullato()) {
            nuoviExtra.put(CHIAVE_TIRI_GIA_FATTI_NEL_TURNO, tiriGiaFattiNelTurno + 1);
        } else {
            nuoviExtra.put(CHIAVE_TIRI_GIA_FATTI_NEL_TURNO, 0);
            nuoviExtra.put(CHIAVE_PUNTI_INIZIO_TURNO, esito.punteggioResiduo());
        }
        MatchState nuovoStato = new MatchState(nuoviPunteggi, statoCorrente.turnoCorrente(),
                statoCorrente.partecipanteAttivo(), nuoviExtra);

        it.uniupo.pissir.darts.game.engine.Esito esitoGenerico = mappaEsito(esito.esito());
        Long vincitore = esito.vittoria() ? evento.participantId() : null;
        return new RuleOutcome(esitoGenerico, nuovoStato, vincitore, esito.motivo(),
                Map.of("turnoAnnullato", esito.turnoAnnullato(), "numeroTurno", statoCorrente.turnoCorrente()));
    }

    /**
     * Converte l'esito delle freccette nel corrispondente esito del motore.
     *
     * @param esito esito del tiro o della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.engine.Esito} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.engine.Esito
     *  
     */
    private static it.uniupo.pissir.darts.game.engine.Esito mappaEsito(Esito esito) {
        return switch (esito) {
            case VALIDO -> it.uniupo.pissir.darts.game.engine.Esito.VALIDO;
            case BUST -> it.uniupo.pissir.darts.game.engine.Esito.BUST;
            case VITTORIA -> it.uniupo.pissir.darts.game.engine.Esito.VITTORIA;
            case IGNORATO -> it.uniupo.pissir.darts.game.engine.Esito.IGNORATO;
        };
    }

    /**
     * Legge un parametro numerico aggiuntivo della regola.
     *
     * @param stato stato da impostare o verificare
     * @param chiave chiave del parametro o dell'elemento
     * @param valoreDefault valore da usare quando il parametro manca
     * @return il valore numerico calcolato o letto
     * @see it.uniupo.pissir.darts.game.engine.MatchState
     *  
     */
    private static int numeroExtra(MatchState stato, String chiave, int valoreDefault) {
        Object valore = stato.extra().get(chiave);
        return valore instanceof Number numero ? numero.intValue() : valoreDefault;
    }

    /**
     * Valuta il tiro applicando punteggio, bust e condizioni di chiusura.
     *
     * @param regola regola di punteggio da applicare
     * @param punteggioCorrente punteggio attuale del partecipante
     * @param puntiInizioTurno punteggio all'inizio del turno
     * @param tiriGiaFattiNelTurno tiri già effettuati nel turno
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.darts.ThrowOutcome} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.darts.ThrowOutcome
     *  
     */
    public static ThrowOutcome valuta(ScoringRule regola, int punteggioCorrente, int puntiInizioTurno,
                                       int tiriGiaFattiNelTurno, DartSector settore, Multiplier moltiplicatore) {
        if (tiriGiaFattiNelTurno >= regola.maxTiriPerTurno()) {
            return new ThrowOutcome(Esito.IGNORATO, 0, punteggioCorrente, false, false,
                    "Numero massimo di tiri per turno già raggiunto");
        }

        OptionalInt puntiOpt = ScoreCalculator.punti(settore, moltiplicatore);
        if (puntiOpt.isEmpty()) {
            return new ThrowOutcome(Esito.IGNORATO, 0, punteggioCorrente, false, false,
                    "Combinazione settore/moltiplicatore non valida: " + settore + "/" + moltiplicatore);
        }
        int punti = puntiOpt.getAsInt();

        boolean decrescente = regola.direzione() == Direzione.DECRESCENTE;
        int nuovoPunteggio = decrescente ? punteggioCorrente - punti : punteggioCorrente + punti;
        int obiettivo = regola.punteggioObiettivo();
        boolean sforamento = decrescente ? nuovoPunteggio < obiettivo : nuovoPunteggio > obiettivo;
        int distanzaDaObiettivo = decrescente ? nuovoPunteggio - obiettivo : obiettivo - nuovoPunteggio;

        if (sforamento) {
            return bust(regola, punteggioCorrente, puntiInizioTurno, "Sforamento: il tiro andrebbe oltre l'obiettivo");
        }
        if (distanzaDaObiettivo == 0) {
            if (!regola.chiusuraDouble() || ScoreCalculator.isDouble(moltiplicatore)) {
                return new ThrowOutcome(Esito.VITTORIA, punti, nuovoPunteggio, false, true,
                        regola.chiusuraDouble() ? "Obiettivo raggiunto con doppio di chiusura" : "Obiettivo raggiunto");
            }
            return bust(regola, punteggioCorrente, puntiInizioTurno,
                    "Obiettivo raggiunto ma senza il doppio di chiusura richiesto");
        }
        if (distanzaDaObiettivo == 1 && regola.chiusuraDouble()) {
            return bust(regola, punteggioCorrente, puntiInizioTurno,
                    "Il tiro lascerebbe un residuo di 1, non chiudibile con un doppio");
        }
        return new ThrowOutcome(Esito.VALIDO, punti, nuovoPunteggio, false, false, "Tiro valido");
    }

    /**
     * Costruisce l'esito del tiro che supera i limiti della regola.
     *
     * @param regola regola di punteggio da applicare
     * @param punteggioCorrente punteggio attuale del partecipante
     * @param puntiInizioTurno punteggio all'inizio del turno
     * @param motivo motivazione associata all'esito
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.darts.ThrowOutcome} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.darts.ThrowOutcome
     *  
     */
    private static ThrowOutcome bust(ScoringRule regola, int punteggioCorrente, int puntiInizioTurno, String motivo) {
        if (regola.bustAnnullaTurno()) {
            return new ThrowOutcome(Esito.BUST, 0, puntiInizioTurno, true, false, motivo);
        }
        return new ThrowOutcome(Esito.BUST, 0, punteggioCorrente, false, false, motivo);
    }
}
