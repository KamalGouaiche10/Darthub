package it.uniupo.pissir.darts.simulator;

import it.uniupo.pissir.darts.simulator.cli.SimulatorConsole;
import it.uniupo.pissir.darts.simulator.generator.Scenario;
import it.uniupo.pissir.darts.simulator.publisher.SensorMqttPublisher;
import it.uniupo.pissir.darts.simulator.service.SimulatorEngine;
import it.uniupo.pissir.darts.simulator.web.SimulatorController;
import it.uniupo.pissir.darts.simulator.web.SimulatorWebServer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Avvia il componente {@code sensor-simulator} e ne collega le dipendenze. 
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private Main() {
    }

    /**
     * Avvia il componente e ne inizializza le risorse.
     *
     * @param args argomenti della riga di comando
     * @throws MqttException se l'operazione non può essere completata
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     *  
     */
    public static void main(String[] args) throws MqttException, InterruptedException {
        Map<String, String> opzioni = parseArgs(args);

        String broker = opzioni.getOrDefault("broker", "tcp://localhost:1883");
        long localeId = Long.parseLong(opzioni.getOrDefault("localeId", "1"));
        long dartboardId = Long.parseLong(opzioni.getOrDefault("dartboardId", "1"));
        long intervallo = Long.parseLong(opzioni.getOrDefault("intervallo", "2000"));
        Scenario scenario = Scenario.valueOf(opzioni.getOrDefault("scenario", "RANDOM").toUpperCase());
        Long seed = opzioni.containsKey("seed") ? Long.parseLong(opzioni.get("seed")) : null;
        int portaWeb = Integer.parseInt(opzioni.getOrDefault("portaWeb", "7200"));

        String clientId = "sensor-simulator-" + localeId + "-" + dartboardId;
        MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(60);
        String mqttUsername = opzioni.get("mqttUsername");
        if (mqttUsername != null && !mqttUsername.isBlank()) {
            options.setUserName(mqttUsername);
            options.setPassword(opzioni.getOrDefault("mqttPassword", "").toCharArray());
        }
        client.connect(options);
        LOG.info("Connesso a {} come {}", broker, clientId);

        SensorMqttPublisher publisher = new SensorMqttPublisher(client, options, localeId, dartboardId);
        SimulatorEngine engine = new SimulatorEngine(publisher, intervallo, scenario, seed);

        SimulatorWebServer webServer = new SimulatorWebServer(new SimulatorController(engine), portaWeb);
        webServer.start();

        if (System.console() != null) {
            SimulatorConsole console = new SimulatorConsole(engine);
            console.avviaLoopComandi();
            webServer.stop();
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } else {
            LOG.info("Nessun terminale interattivo rilevato: console a riga di comando disabilitata. "
                    + "Il processo resta in esecuzione per il server web su http://localhost:{} "
                    + "(vedi sensor-simulator/README.md).", portaWeb);
            new CountDownLatch(1).await();
        }
    }

    /**
     * Legge e converte args dalla richiesta o dalla configurazione.
     *
     * @param args argomenti della riga di comando
     * @return la mappa dei dati elaborati
     * @see java.util.Map
     *  
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opzioni = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--") && i + 1 < args.length) {
                opzioni.put(arg.substring(2), args[i + 1]);
                i++;
            }
        }
        return opzioni;
    }
}
