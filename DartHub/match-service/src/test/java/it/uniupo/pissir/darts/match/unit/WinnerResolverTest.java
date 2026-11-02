package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.match.model.MatchParticipant;
import it.uniupo.pissir.darts.match.service.WinnerResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code WinnerResolver} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.model.ScoringRule
 *  
 */
class WinnerResolverTest {

    private static final ScoringRule REGOLA_DECRESCENTE = new ScoringRule(1, 1, "501", Direzione.DECRESCENTE,
            501, 0, true, true, 3, "{}");
    private static final ScoringRule REGOLA_CRESCENTE = new ScoringRule(2, 1, "cricket", Direzione.CRESCENTE,
            0, 100, false, true, 3, "{}");

    /**
     * Verifica {@code vincitoreEsplicito} nel caso primo in classifica nonostante il punteggio. 
     */
    @Test
    void vincitoreEsplicito_primoInClassificaNonostanteIlPunteggio() {
        MatchParticipant p1 = partecipante(1, 40);
        MatchParticipant p2 = partecipante(2, 0);

        List<WinnerResolver.Piazzamento> risultato = WinnerResolver.risolvi(List.of(p1, p2), REGOLA_DECRESCENTE, 1L);

        WinnerResolver.Piazzamento primo = risultato.get(0);
        assertEquals(1, primo.participantId());
        assertEquals(1, primo.posizione());
        assertTrue(primo.vincitore());
    }

    /**
     * Verifica {@code senzaVincitoreEsplicito} nel caso decrescente nessuna vittoria. 
     */
    @Test
    void senzaVincitoreEsplicito_decrescente_nessunaVittoria() {
        MatchParticipant p1 = partecipante(1, 40);
        MatchParticipant p2 = partecipante(2, 10);

        List<WinnerResolver.Piazzamento> risultato = WinnerResolver.risolvi(List.of(p1, p2), REGOLA_DECRESCENTE, null);

        assertEquals(2, risultato.get(0).participantId());
        assertTrue(risultato.stream().noneMatch(WinnerResolver.Piazzamento::vincitore));
    }

    /**
     * Verifica {@code senzaVincitoreEsplicito} nel caso crescente nessuna vittoria. 
     */
    @Test
    void senzaVincitoreEsplicito_crescente_nessunaVittoria() {
        MatchParticipant p1 = partecipante(1, 40);
        MatchParticipant p2 = partecipante(2, 10);

        List<WinnerResolver.Piazzamento> risultato = WinnerResolver.risolvi(List.of(p1, p2), REGOLA_CRESCENTE, null);

        assertEquals(1, risultato.get(0).participantId());
        assertTrue(risultato.stream().noneMatch(WinnerResolver.Piazzamento::vincitore));
    }

    /**
     * Verifica il caso posizioni assegnate in ordine di merito. 
     */
    @Test
    void posizioniAssegnateInOrdineDiMerito() {
        MatchParticipant p1 = partecipante(1, 40);
        MatchParticipant p2 = partecipante(2, 10);
        MatchParticipant p3 = partecipante(3, 25);

        List<WinnerResolver.Piazzamento> risultato = WinnerResolver.risolvi(List.of(p1, p2, p3), REGOLA_DECRESCENTE, null);

        assertEquals(1, posizioneDi(risultato, 2));
        assertEquals(25, punteggioDi(risultato, 3));
        assertEquals(2, posizioneDi(risultato, 3));
        assertEquals(3, posizioneDi(risultato, 1));
    }

    /**
     * Verifica {@code soloUnVincitore} nel caso anche con vincitore esplicito. 
     */
    @Test
    void soloUnVincitore_ancheConVincitoreEsplicito() {
        MatchParticipant p1 = partecipante(1, 0);
        MatchParticipant p2 = partecipante(2, 0);

        List<WinnerResolver.Piazzamento> risultato = WinnerResolver.risolvi(List.of(p1, p2), REGOLA_DECRESCENTE, 2L);

        long conteggioVincitori = risultato.stream().filter(WinnerResolver.Piazzamento::vincitore).count();
        assertEquals(1, conteggioVincitori);
        assertFalse(risultato.stream().filter(p -> p.participantId() == 1).findFirst().orElseThrow().vincitore());
    }

    /**
     * Legge la posizione del partecipante in classifica.
     *
     * @param risultato insieme dei dati relativi a risultato
     * @param participantId identificativo del partecipante
     * @return il valore numerico calcolato o letto
     * @see java.util.List
     *  
     */
    private static int posizioneDi(List<WinnerResolver.Piazzamento> risultato, long participantId) {
        return risultato.stream().filter(p -> p.participantId() == participantId).findFirst().orElseThrow().posizione();
    }

    /**
     * Legge il punteggio associato al partecipante indicato.
     *
     * @param risultato insieme dei dati relativi a risultato
     * @param participantId identificativo del partecipante
     * @return il valore numerico calcolato o letto
     * @see java.util.List
     *  
     */
    private static int punteggioDi(List<WinnerResolver.Piazzamento> risultato, long participantId) {
        return risultato.stream().filter(p -> p.participantId() == participantId).findFirst().orElseThrow().punteggioFinale();
    }

    /**
     * Prepara il partecipante alla partita con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del winner resolver
     * @param punteggio punteggio da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.match.model.MatchParticipant} prodotto o recuperato
     * @see it.uniupo.pissir.darts.match.model.MatchParticipant
     *  
     */
    private static MatchParticipant partecipante(long id, int punteggio) {
        return new MatchParticipant(id, 100, id, null, (int) id, punteggio, false);
    }
}
