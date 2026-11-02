package it.uniupo.pissir.darts.match.service;

import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.model.MatchParticipant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Determina il vincitore e le posizioni finali: obiettivo raggiunto con chiusura valida (vincitoreEsplicito passato da ThrowIngestionService sull'esito VITTORIA), oppure — se la partita è chiusa manualmente — il miglior punteggio residuo. 
 */
public final class WinnerResolver {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private WinnerResolver() {
    }

    /**
     * Raccoglie i dati di piazzamento.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param punteggioFinale punteggio alla fine della partita
     * @param posizione posizione in classifica
     * @param vincitore partecipante vincitore
     *  
     */
    public record Piazzamento(long participantId, int punteggioFinale, int posizione, boolean vincitore) {
    }

    /**
     * Individua il destinatario o il risultato corrispondente ai dati ricevuti.
     *
     * @param partecipanti partecipanti associati alla partita
     * @param regola regola di punteggio da applicare
     * @param vincitoreEsplicito vincitore esplicito da utilizzare
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public static List<Piazzamento> risolvi(List<MatchParticipant> partecipanti, ScoringRule regola, Long vincitoreEsplicito) {
        Comparator<MatchParticipant> perMerito = regola.direzione() == Direzione.DECRESCENTE
                ? Comparator.comparingInt(MatchParticipant::punteggioCorrente)
                : Comparator.comparingInt(MatchParticipant::punteggioCorrente).reversed();

        List<MatchParticipant> ordinati = new ArrayList<>(partecipanti);
        if (vincitoreEsplicito != null) {
            ordinati.sort((a, b) -> {
                if (a.id() == vincitoreEsplicito) {
                    return -1;
                }
                if (b.id() == vincitoreEsplicito) {
                    return 1;
                }
                return perMerito.compare(a, b);
            });
        } else {
            ordinati.sort(perMerito);
        }

        List<Piazzamento> risultato = new ArrayList<>();
        for (int i = 0; i < ordinati.size(); i++) {
            MatchParticipant partecipante = ordinati.get(i);
            boolean vincitore = vincitoreEsplicito != null && partecipante.id() == vincitoreEsplicito;
            risultato.add(new Piazzamento(partecipante.id(), partecipante.punteggioCorrente(), i + 1, vincitore));
        }
        return risultato;
    }
}
