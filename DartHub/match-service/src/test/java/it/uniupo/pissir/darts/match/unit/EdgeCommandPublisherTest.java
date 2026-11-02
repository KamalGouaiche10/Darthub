package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.common.exception.ServiceUnavailableException;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttOperationException;
import it.uniupo.pissir.darts.match.dto.EdgeCommandDTO;
import it.uniupo.pissir.darts.match.mqtt.EdgeCommandPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifica il comportamento di {@code EdgeCommandPublisher} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
 *  
 */
@ExtendWith(MockitoExtension.class)
class EdgeCommandPublisherTest {

    @Mock
    private MqttEventBus eventBus;

    /**
     * Verifica {@code publishCommandOrThrow} nel caso evento bus assente non lancia niente. 
     */
    @Test
    void publishCommandOrThrow_eventBusNull_nonLanciaNiente() {
        EdgeCommandPublisher publisher = new EdgeCommandPublisher(null);

        publisher.publishCommandOrThrow(1L, 1L, EdgeCommandDTO.endMatch(1L));

    }

    /**
     * Verifica {@code publishCommandOrThrow} nel caso pubblicazione riuscita non lancia niente. 
     */
    @Test
    void publishCommandOrThrow_pubblicazioneRiuscita_nonLanciaNiente() {
        EdgeCommandPublisher publisher = new EdgeCommandPublisher(eventBus);

        publisher.publishCommandOrThrow(1L, 1L, EdgeCommandDTO.endMatch(1L));

        verify(eventBus).publishOrThrow(anyString(), any(), anyInt(), org.mockito.ArgumentMatchers.eq(true));
    }

    /**
     * Verifica {@code publishCommandOrThrow} nel caso pubblicazione fallita servizio unavailable. 
     */
    @Test
    void publishCommandOrThrow_pubblicazioneFallita_serviceUnavailable() {
        EdgeCommandPublisher publisher = new EdgeCommandPublisher(eventBus);
        doThrow(new MqttOperationException("broker giù", new RuntimeException()))
                .when(eventBus).publishOrThrow(anyString(), any(), anyInt(), org.mockito.ArgumentMatchers.eq(true));

        assertThrows(ServiceUnavailableException.class,
                () -> publisher.publishCommandOrThrow(1L, 1L, EdgeCommandDTO.endMatch(1L)));
    }
}
