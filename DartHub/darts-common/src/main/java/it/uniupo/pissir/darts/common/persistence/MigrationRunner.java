package it.uniupo.pissir.darts.common.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applica in ordine le migrazioni SQL non ancora registrate nel database. 
 */
public final class MigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");
    private static final Pattern SECTION_PATTERN = Pattern.compile("(?m)^-- @version (\\d+)[ \\t]*\\r?$");

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private MigrationRunner() {
    }

    /**
     * Applica le migrazioni SQL non ancora eseguite.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param migrationsResourceDir cartella delle migrazioni nel classpath
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public static void run(ConnectionProvider connectionProvider, String migrationsResourceDir) {
        Set<Integer> applied = fetchAppliedVersions(connectionProvider);
        for (String resourceName : listMigrationResources(migrationsResourceDir)) {
            Matcher matcher = VERSION_PATTERN.matcher(resourceName);
            if (!matcher.matches()) {
                continue;
            }
            String resourcePath = migrationsResourceDir + "/" + resourceName;
            int fileVersion = Integer.parseInt(matcher.group(1));
            for (Migration migration : readMigrations(resourcePath, fileVersion)) {
                if (applied.contains(migration.version())) {
                    continue;
                }
                applyMigration(connectionProvider, resourcePath, migration.sql(), migration.version());
                applied.add(migration.version());
            }
        }
    }

    /**
     * I file consolidati separano le versioni storiche con commenti {@code -- @version N}.
     * Ogni sezione mantiene la propria transazione e registrazione in schema_version.
     * I normali file senza sezioni continuano a usare la versione nel nome.
     */
    private static List<Migration> readMigrations(String resourcePath, int fileVersion) {
        String sql = SqlScripts.readResource(resourcePath);
        Matcher sections = SECTION_PATTERN.matcher(sql);
        if (!sections.find()) {
            return List.of(new Migration(fileVersion, sql));
        }
        if (!sql.substring(0, sections.start()).isBlank()
                || Integer.parseInt(sections.group(1)) != fileVersion) {
            throw new IllegalStateException("Prima sezione non valida: " + resourcePath);
        }
        List<Migration> migrations = new ArrayList<>();
        int version = fileVersion;
        int start = sections.end();
        while (sections.find()) {
            migrations.add(new Migration(version, sql.substring(start, sections.start())));
            int nextVersion = Integer.parseInt(sections.group(1));
            if (nextVersion <= version) {
                throw new IllegalStateException("Versioni non crescenti: " + resourcePath);
            }
            version = nextVersion;
            start = sections.end();
        }
        migrations.add(new Migration(version, sql.substring(start)));
        return migrations;
    }

    private record Migration(int version, String sql) { }

    /**
     * Legge le versioni delle migrazioni già applicate.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.Set
     *  
     */
    private static Set<Integer> fetchAppliedVersions(ConnectionProvider connectionProvider) {
        return connectionProvider.withConnection(connection -> {
            Set<Integer> versions = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT version FROM schema_version")) {
                while (rs.next()) {
                    versions.add(rs.getInt(1));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Lettura di schema_version fallita", e);
            }
            return versions;
        });
    }

    /**
     * Esegue una migrazione e ne registra la versione.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param resourcePath percorso della risorsa nel classpath
     * @param version version da utilizzare
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    private static void applyMigration(ConnectionProvider connectionProvider, String resourcePath, String sql, int version) {
        connectionProvider.withTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String stmt : SqlScripts.splitStatements(sql)) {
                    statement.execute(stmt);
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, Instant.now().toString());
                    ps.executeUpdate();
                }
                return null;
            } catch (SQLException e) {
                throw new IllegalStateException("Migrazione fallita: " + resourcePath, e);
            }
        });
        LOG.info("Applicata migrazione {} (versione {})", resourcePath, version);
    }

    /**
     * Elenca gli script SQL della cartella delle migrazioni.
     *
     * @param dir cartella da esaminare
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    private static List<String> listMigrationResources(String dir) {
        try {
            URL url = MigrationRunner.class.getClassLoader().getResource(dir);
            if (url == null) {
                return List.of();
            }
            List<String> names = "jar".equals(url.getProtocol())
                    ? listFromJar(url, dir)
                    : listFromDirectory(url);
            names.sort(Comparator.naturalOrder());
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("Lettura delle migrazioni fallita: " + dir, e);
        }
    }

    /**
     * Elenca le risorse SQL contenute nel file JAR.
     *
     * @param url indirizzo della risorsa HTTP
     * @param dir cartella da esaminare
     * @return gli elementi trovati o prodotti dall'operazione
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     * @see java.util.List
     *  
     */
    private static List<String> listFromJar(URL url, String dir) throws Exception {
        String path = url.getPath();
        String jarPath = path.substring(5, path.indexOf('!'));
        List<String> names = new ArrayList<>();
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        try (JarFile jar = new JarFile(java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".sql")) {
                    names.add(name.substring(prefix.length()));
                }
            }
        }
        return names;
    }

    /**
     * Elenca gli script SQL presenti nella cartella.
     *
     * @param url indirizzo della risorsa HTTP
     * @return gli elementi trovati o prodotti dall'operazione
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     * @see java.util.List
     *  
     */
    private static List<String> listFromDirectory(URL url) throws Exception {
        List<String> names = new ArrayList<>();
        File folder = new File(url.toURI());
        File[] files = folder.listFiles((d, name) -> name.endsWith(".sql"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }
}
