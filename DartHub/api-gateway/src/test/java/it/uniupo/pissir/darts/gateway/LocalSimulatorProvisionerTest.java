package it.uniupo.pissir.darts.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica il comportamento di {@code LocalSimulatorProvisioner} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class LocalSimulatorProvisionerTest {
    @TempDir Path root;

    /**
     * Verifica l'avvio del sensore configurato e il riuso del processo nelle aperture successive.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void startsConfiguredSensorAndReusesSameProcessAcrossOpenings() throws Exception {
        int port = freePort();
        long boardId = port - 7400;
        Path config = root.resolve("data/edge-instances/edge-42.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "edge.localeId=2\nedge.dartboardId=" + boardId
                + "\nmqtt.brokerLocale=tcp://test-local-broker:1884\n");
        Files.writeString(root.resolve("data/edge-device-42.secret"), "test-only");
        createSensorJar();
        LocalSimulatorProvisioner provisioner = new LocalSimulatorProvisioner(root);
        Path pidPath = root.resolve("logs/pid/sensor-simulator-auto-42.pid");
        ProcessHandle process = null;
        try {
            String url = provisioner.ensure(2, boardId);
            assertEquals("http://127.0.0.1:" + port, url);
            long pid = Long.parseLong(Files.readString(pidPath));
            process = ProcessHandle.of(pid).orElseThrow();
            assertTrue(process.isAlive());
            assertEquals(url, provisioner.ensure(2, boardId));
            assertEquals(pid, Long.parseLong(Files.readString(pidPath)));
            assertEquals(url, new LocalSimulatorProvisioner(root).ensure(2, boardId));
            assertEquals(pid, Long.parseLong(Files.readString(pidPath)));
            assertTrue(Files.readString(config).contains("test-local-broker"));
        } finally {
            if (process != null) {
                process.destroyForcibly();
                process.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Verifica che una porta occupata da un altro sensore impedisca l'avvio.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void refusesAnOccupiedPortWithAnotherSensorIdentity() throws Exception {
        HttpServer other = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        other.createContext("/simulatore/stato", exchange -> {
            byte[] body = "{\"localeId\":1,\"dartboardId\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        other.start();
        try {
            int port = other.getAddress().getPort();
            IOException error = assertThrows(IOException.class,
                    () -> new LocalSimulatorProvisioner(root).ensure(2, port - 7400));
            assertTrue(error.getMessage().contains("occupata"));
            assertFalse(Files.exists(root.resolve("logs/pid")));
        } finally {
            other.stop(0);
        }
    }

    /**
     * Verifica che un bersaglio senza configurazione edge non avvii il simulatore.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void neverStartsAnUnconfiguredBoard() throws Exception {
        int port = freePort();
        IOException error = assertThrows(IOException.class,
                () -> new LocalSimulatorProvisioner(root).ensure(2, port - 7400));
        assertTrue(error.getMessage().contains("non ha un edge configurato"));
        assertFalse(Files.exists(root.resolve("logs/pid")));
    }

    /**
     * Verifica l'intervallo delle porte e le associazioni previste dalla demo. 
     */
    @Test
    void validatesPortRangeAndKeepsExistingDemoMappings() {
        assertEquals(7200, LocalSimulatorProvisioner.portFor(1, 1));
        assertEquals(7207, LocalSimulatorProvisioner.portFor(5, 7));
        assertEquals(7404, LocalSimulatorProvisioner.portFor(2, 4));
        assertThrows(IllegalArgumentException.class, () -> LocalSimulatorProvisioner.portFor(0, 4));
        assertThrows(IllegalArgumentException.class, () -> LocalSimulatorProvisioner.portFor(Long.MAX_VALUE, 4));
        assertThrows(IllegalArgumentException.class, () -> LocalSimulatorProvisioner.portFor(583, 58335));
    }

    /**
     * Individua una porta TCP libera per il test.
     *
     * @return una porta TCP disponibile per il test
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /**
     * Prepara il JAR del sensore HTTP usato nei test.
     *
     * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
     *  
     */
    private void createSensorJar() throws IOException {
        Path jar = root.resolve("sensor-simulator/target/sensor-simulator.jar");
        Files.createDirectories(jar.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, FakeSensor.class.getName());
        String entry = FakeSensor.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
             var in = FakeSensor.class.getClassLoader().getResourceAsStream(entry)) {
            out.putNextEntry(new JarEntry(entry));
            in.transferTo(out);
            out.closeEntry();
        }
    }

    /**
     * Fornisce un sensore HTTP di prova per verificare avvio e riuso dei simulatori. 
     */
    public static class FakeSensor {
        /**
         * Avvia il componente di prova e ne inizializza le risorse.
         *
         * @param args argomenti della riga di comando
         * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
         *  
         */
        public static void main(String[] args) throws Exception {
            java.util.Map<String, String> options = new java.util.HashMap<>();
            for (int i = 0; i + 1 < args.length; i += 2) options.put(args[i], args[i + 1]);
            if (!"tcp://test-local-broker:1884".equals(options.get("--broker"))) System.exit(2);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",
                    Integer.parseInt(options.get("--portaWeb"))), 0);
            String state = "{\"localeId\":" + options.get("--localeId")
                    + ",\"dartboardId\":" + options.get("--dartboardId") + "}";
            server.createContext("/simulatore/stato", exchange -> {
                byte[] body = state.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
        }
    }
}
