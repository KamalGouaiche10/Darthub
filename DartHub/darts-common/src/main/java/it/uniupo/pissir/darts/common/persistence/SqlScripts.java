package it.uniupo.pissir.darts.common.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Legge gli script SQL dal classpath e li divide in istruzioni. 
 */
final class SqlScripts {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private SqlScripts() {
    }

    /**
     * Legge il testo di una risorsa dal classpath.
     *
     * @param path percorso o indirizzo della risorsa
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    static String readResource(String path) {
        try (InputStream in = SqlScripts.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Risorsa non trovata: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lettura risorsa fallita: " + path, e);
        }
    }

    /**
     * Divide lo script SQL nelle istruzioni da eseguire.
     *
     * @param sql istruzione SQL da eseguire
     * @return le istruzioni SQL non vuote ricavate dallo script
     * @see java.util.List
     *  
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        for (String part : sql.split(";")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }
}
