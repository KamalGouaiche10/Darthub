package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.model.Throw;

import java.util.List;

/**
 * Giocatore attivo, tiri per turno, passaggio del turno. 
 */
public final class TurnManager {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private TurnManager() {
    }

    /**
     * Turno corrente: chi tira, quale numero di turno, quanti tiri già fatti, punteggio a inizio turno.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param numeroTurno numero del turno
     * @param tiriGiaFattiNelTurno tiri già effettuati nel turno
     * @param puntiInizioTurno punteggio all'inizio del turno
     *  
     */
    public record TurnState(long participantId, int numeroTurno, int tiriGiaFattiNelTurno, int puntiInizioTurno) {
    }

    /**
     * Calcola turno corrente per il turn manager.
     *
     * @param partecipantiAttivi insieme dei dati relativi a partecipanti attivi
     * @param tiriRilevanti insieme dei dati relativi a tiri rilevanti
     * @param regola regola di punteggio da applicare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.service.TurnManager.TurnState} prodotto o recuperato
     * @throws IllegalStateException se viene rilevato l'errore «Nessun partecipante attivo nella partita»
     * @see it.uniupo.pissir.darts.match.service.TurnManager.TurnState
     *  
     */
    public static TurnState calcolaTurnoCorrente(List<MatchParticipant> partecipantiAttivi, List<Throw> tiriRilevanti,
                                                  ScoringRule regola) {
        if (partecipantiAttivi.isEmpty()) {
            throw new IllegalStateException("Nessun partecipante attivo nella partita");
        }
        if (tiriRilevanti.isEmpty()) {
            MatchParticipant primo = partecipantiAttivi.get(0);
            return new TurnState(primo.id(), 1, 0, primo.punteggioCorrente());
        }

        Throw ultimo = tiriRilevanti.get(tiriRilevanti.size() - 1);
        long tiriStessoTurno = tiriRilevanti.stream()
                .filter(t -> t.participantId() == ultimo.participantId() && t.numeroTurno() == ultimo.numeroTurno())
                .count();
        boolean turnoTerminato = ultimo.esito() != Esito.VALIDO || tiriStessoTurno >= regola.maxTiriPerTurno();

        if (!turnoTerminato) {
            MatchParticipant attuale = trovaPartecipante(partecipantiAttivi, ultimo.participantId());
            int puntiApplicatiInQuestoTurno = (int) tiriRilevanti.stream()
                    .filter(t -> t.participantId() == ultimo.participantId() && t.numeroTurno() == ultimo.numeroTurno()
                            && t.esito() == Esito.VALIDO)
                    .mapToInt(Throw::punti).sum();
            int segno = regola.direzione() == Direzione.DECRESCENTE ? 1 : -1;
            int puntiInizioTurno = attuale.punteggioCorrente() + segno * puntiApplicatiInQuestoTurno;
            return new TurnState(ultimo.participantId(), ultimo.numeroTurno(), (int) tiriStessoTurno, puntiInizioTurno);
        }

        MatchParticipant prossimo = prossimoPartecipante(partecipantiAttivi, ultimo.participantId());
        int prossimoNumeroTurno = contaTurniPrecedenti(tiriRilevanti, prossimo.id()) + 1;
        return new TurnState(prossimo.id(), prossimoNumeroTurno, 0, prossimo.punteggioCorrente());
    }

    /**
     * Trova partecipante per il turn manager.
     *
     * @param partecipanti partecipanti associati alla partita
     * @param id identificativo del turn manager
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @throws IllegalStateException se viene rilevato l'errore «non trovato o non più attivo»
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private static MatchParticipant trovaPartecipante(List<MatchParticipant> partecipanti, long id) {
        return partecipanti.stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Partecipante " + id + " non trovato o non più attivo"));
    }

    /**
     * Individua il prossimo partecipante attivo.
     *
     * @param partecipantiAttivi insieme dei dati relativi a partecipanti attivi
     * @param ultimoParticipantId identificativo del ultimo partecipante
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private static MatchParticipant prossimoPartecipante(List<MatchParticipant> partecipantiAttivi, long ultimoParticipantId) {
        int indice = -1;
        for (int i = 0; i < partecipantiAttivi.size(); i++) {
            if (partecipantiAttivi.get(i).id() == ultimoParticipantId) {
                indice = i;
                break;
            }
        }

        int prossimoIndice = indice == -1 ? 0 : (indice + 1) % partecipantiAttivi.size();
        return partecipantiAttivi.get(prossimoIndice);
    }

    /**
     * Conta turni precedenti per il turn manager.
     *
     * @param tiriRilevanti insieme dei dati relativi a tiri rilevanti
     * @param participantId identificativo del partecipante
     * @return il numero di elementi corrispondenti ai criteri
     * @see java.util.List
     *  
     */
    private static int contaTurniPrecedenti(List<Throw> tiriRilevanti, long participantId) {
        return (int) tiriRilevanti.stream()
                .filter(t -> t.participantId() == participantId)
                .mapToInt(Throw::numeroTurno)
                .distinct()
                .count();
    }
}
