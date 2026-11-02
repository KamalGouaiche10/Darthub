package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.edge.model.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Controlla la disponibilità dei collegamenti dell'edge con il sistema centrale. 
 */
public final class ConnectionMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionMonitor.class);

    private volatile ConnectionState stato = ConnectionState.OFFLINE;
    private final List<Consumer<ConnectionState>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Gestisce il completamento della connessione MQTT.
     *
     * @param reconnect indicatore di riconnessione del client
     *  
     */
    public void onConnectComplete(boolean reconnect) {
        LOG.info("Broker centrale: connesso ({})", reconnect ? "riconnessione" : "prima connessione");
        stato = ConnectionState.ONLINE;
        notifica();
    }

    /**
     * Gestisce la perdita della connessione MQTT.
     *
     * @param cause causa originale dell'errore
     *  
     */
    public void onConnectionLost(Throwable cause) {
        LOG.warn("Broker centrale: connessione persa ({})", cause == null ? "motivo sconosciuto" : cause.getMessage());
        stato = ConnectionState.OFFLINE;
        notifica();
    }

    /**
     * Indica se il collegamento con il sistema centrale è disponibile.
     *
     * @return {@code true} se il collegamento con il sistema centrale è disponibile; {@code false} altrimenti
     *  
     */
    public boolean isOnline() {
        return stato == ConnectionState.ONLINE;
    }

    /**
     * Registra un ascoltatore delle variazioni di connessione.
     *
     * @param listener ascoltatore da registrare
     * @see java.util.function.Consumer
     *  
     */
    public void addListener(Consumer<ConnectionState> listener) {
        listeners.add(listener);
    }

    /**
     * Avvisa gli ascoltatori del nuovo stato del collegamento. 
     */
    private void notifica() {
        for (Consumer<ConnectionState> listener : listeners) {
            try {
                listener.accept(stato);
            } catch (RuntimeException e) {
                LOG.error("Listener di ConnectionMonitor fallito", e);
            }
        }
    }
}
