package it.uniupo.pissir.darts.gateway.unit;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.gateway.monitoring.BrokerProbe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifica il comportamento di {@code BrokerProbe} con casi di prova JUnit. 
 */
class BrokerProbeTest {

    /**
     * Verifica {@code verifica} nel caso senza evento bus falso senza tentare pubblicazione. 
     */
    @Test
    void verifica_senzaEventBus_falseSenzaTentarePubblicazione() {
        BrokerProbe probe = new BrokerProbe(null);

        assertFalse(probe.verifica());
    }

    /**
     * Verifica {@code verifica} nel caso pubblicazione fallita falso. 
     */
    @Test
    void verifica_pubblicazioneFallita_false() {
        MqttEventBus eventBus = org.mockito.Mockito.mock(MqttEventBus.class);
        doThrow(new RuntimeException("broker irraggiungibile")).when(eventBus)
                .publish(anyString(), any(), anyInt());

        BrokerProbe probe = new BrokerProbe(eventBus);

        assertFalse(probe.verifica());
        verify(eventBus).publish(anyString(), any(), anyInt());
    }
}
