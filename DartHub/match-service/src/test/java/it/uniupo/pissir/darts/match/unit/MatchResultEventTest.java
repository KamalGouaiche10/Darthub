package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.match.dao.MatchDao;
import it.uniupo.pissir.darts.match.dao.MatchResultDao;
import it.uniupo.pissir.darts.match.model.Match;
import it.uniupo.pissir.darts.match.model.MatchStatus;
import it.uniupo.pissir.darts.match.model.ModalitaPartita;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;
import it.uniupo.pissir.darts.match.service.DartboardStatusClient;
import it.uniupo.pissir.darts.match.service.MatchResultService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Verifica il comportamento di {@code MatchResultEvent} con casi di prova JUnit. 
 */
class MatchResultEventTest {
    /**
     * Verifica il caso ripubblicare lo stesso esito mantiene l identificativo evento. 
     */
    @Test
    void ripubblicareLoStessoEsitoMantieneLIdEvento() {
        MatchResultService service = new MatchResultService(mock(MatchResultDao.class), mock(MatchDao.class),
                mock(DartboardStatusClient.class), mock(EdgeCommandPublisher.class),
                mock(MatchEventPublisher.class));
        Match match = new Match(7, 3, 2, 1, 1, ModalitaPartita.INDIVIDUALE,
                MatchStatus.COMPLETATA, 63, null, 0, null, null);

        var prima = service.completedEvent(match, List.of(), List.of());
        var seconda = service.completedEvent(match, List.of(), List.of());

        assertEquals("match-completed-7", prima.eventId());
        assertEquals(prima.eventId(), seconda.eventId());
    }
}
