package it.uniupo.pissir.darts.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code LocalEdgeProvisioner} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class LocalEdgeProvisionerTest {
    @TempDir Path root;

    /**
     * Verifica il caso saves token separately e mantiene configuration per restart quando inizio fails.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void savesTokenSeparatelyAndKeepsConfigurationForRestartWhenStartFails() throws Exception {
        Path jar = root.resolve("edge-component/target/edge-component.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "invalid test jar");

        LocalEdgeProvisioner provisioner = new LocalEdgeProvisioner(root);
        assertThrows(java.io.IOException.class, () -> provisioner.provision(
                42, 2, 3, 1, "EDGE-DEMO-42", "test-token-only", 30, null));

        Path secret = root.resolve("data/edge-device-42.secret");
        Path configFile = root.resolve("data/edge-instances/edge-42.properties");
        assertEquals("test-token-only", Files.readString(secret));
        Properties config = new Properties();
        try (var in = Files.newInputStream(configFile)) {
            config.load(in);
        }
        assertEquals("2", config.getProperty("edge.localeId"));
        assertEquals("3", config.getProperty("edge.dartboardId"));
        assertEquals("data/edge-device-42.secret", config.getProperty("edge.pathToken"));
        assertFalse(Files.readString(configFile).contains("test-token-only"));
        assertTrue(Integer.parseInt(config.getProperty("edge.portaDisplay")) >= 7300);

        provisioner.remove(42);
        assertFalse(Files.exists(secret));
        assertFalse(Files.exists(configFile));
    }

    /**
     * Verifica il caso removes legacy token e pid senza deleting its properties.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void removesLegacyTokenAndPidWithoutDeletingItsProperties() throws Exception {
        Path config = root.resolve("edge-component/edge-2-3.properties");
        Path token = root.resolve("data/edge-2-3-token.secret");
        Path pid = root.resolve("logs/pid/edge-component-2-3.pid");
        Files.createDirectories(config.getParent());
        Files.createDirectories(token.getParent());
        Files.createDirectories(pid.getParent());
        Files.writeString(config, "edge.localeId=2\nedge.dartboardId=3\n");
        Files.writeString(token, "test-token-only");
        Files.writeString(pid, "99999999");

        new LocalEdgeProvisioner(root).remove(31, 2, 3);

        assertFalse(Files.exists(token));
        assertFalse(Files.exists(pid));
        assertTrue(Files.exists(config));
    }

    /**
     * Verifica il caso stops soltanto the managed edge processo.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test
    void stopsOnlyTheManagedEdgeProcess() throws Exception {
        Path jar = root.resolve("edge-component/target/edge-component.jar");
        Path config = root.resolve("data/edge-instances/edge-77.properties");
        Path pid = root.resolve("logs/pid/edge-component-auto-77.pid");
        Files.createDirectories(jar.getParent());
        Files.createDirectories(config.getParent());
        Files.createDirectories(pid.getParent());
        Files.writeString(config, "edge.portaDisplay=7300\n");
        Files.writeString(root.resolve("data/edge-device-77.secret"), "test-token-only");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Sleeper.class.getName());
        String entryName = Sleeper.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
             var in = Sleeper.class.getClassLoader().getResourceAsStream(entryName)) {
            out.putNextEntry(new JarEntry(entryName));
            in.transferTo(out);
            out.closeEntry();
        }
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.toString(), "-Dconfig.file=" + config,
                "-jar", jar.toString()).start();
        try {
            String command = process.info().commandLine().orElse("").replace('\\', '/');
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    command.contains("edge-component/target/edge-component.jar")
                            && command.contains("edge-77.properties"),
                    "Windows non espone la riga di comando del processo figlio in questo ambiente");
            Files.writeString(pid, Long.toString(process.pid()));
            new LocalEdgeProvisioner(root).remove(77);
            assertFalse(process.isAlive());
            assertFalse(Files.exists(config));
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Gestisce i dati e le operazioni di sleeper. 
     */
    public static class Sleeper {
        /**
         * Avvia il componente di prova e ne inizializza le risorse.
         *
         * @param args argomenti della riga di comando
         * @throws InterruptedException se il thread viene interrotto durante l'attesa
         *  
         */
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60000);
        }
    }
}
