package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.match.mqtt.MatchEventPublisher;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

/**
 * Verifica il comportamento di {@code MatchCompletionDelivery} con casi di prova JUnit. 
 */
class MatchCompletionDeliveryTest {
    /**
     * Verifica il caso esito conservato per partita mantiene anche il canale precedente. 
     */
    @Test
    void esitoConservatoPerPartitaMantieneAncheIlCanalePrecedente() {
        var bus = mock(MqttEventBus.class);
        var event = new MatchCompletedEventDTO("match-completed-7", 7, 1, 2, 3,
                "INDIVIDUALE", List.of());
        new MatchEventPublisher(bus).publishMatchCompleted(event);
        verify(bus).publish(MqttTopics.EVENTI_PARTITE_COMPLETATA, event, 2);
        verify(bus).publish(MqttTopics.EVENTI_PARTITE_COMPLETATA + "/7", event, 2, true);
    }
}
