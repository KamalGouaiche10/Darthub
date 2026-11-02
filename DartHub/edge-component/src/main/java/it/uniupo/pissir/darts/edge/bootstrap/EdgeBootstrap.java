package it.uniupo.pissir.darts.edge.bootstrap;

import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.edge.config.EdgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Registra automaticamente questo edge presso locale-service invece del flusso manuale 
 */
public final class EdgeBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(EdgeBootstrap.class);

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private EdgeBootstrap() {
    }

    /**
     * Restituisce il percorso del file del token.
     *
     * @param properties proprietà di configurazione
     * @return percorso del token del dispositivo
     * @see java.nio.file.Path
     *  
     */
    public static Path tokenFilePath(EdgeProperties properties) {
        return Path.of(properties.pathToken());
    }

    /**
     * Registra il dispositivo edge applicando i controlli previsti.
     *
     * @param properties proprietà di configurazione
     * @param localeServiceUrl indirizzo di base del locale servizio
     * @param userServiceUrl indirizzo di base dell'utente servizio
     * @param username nome dell'account
     * @param password password da verificare o registrare
     * @param serialNumber numero seriale del dispositivo
     * @see it.uniupo.pissir.darts.edge.config.EdgeProperties
     *  
     */
    public static void register(EdgeProperties properties, String localeServiceUrl, String userServiceUrl,
                                 String username, String password, String serialNumber) {
        ServiceHttpClient httpClient = new ServiceHttpClient(Duration.ofSeconds(10));

        Map<?, ?> login = httpClient.post(userServiceUrl + "/auth/login",
                Map.of("username", username, "password", password), null, Map.class);
        String accessToken = String.valueOf(login.get("accessToken"));

        Map<?, ?> registrazione = httpClient.post(localeServiceUrl + "/edge-devices/register",
                Map.of("dartboardId", properties.dartboardId(), "serialNumber", serialNumber), accessToken, Map.class);
        String tokenEdge = String.valueOf(registrazione.get("token"));

        Path tokenFile = tokenFilePath(properties);
        try {
            Files.writeString(tokenFile, tokenEdge);
        } catch (IOException e) {
            throw new IllegalStateException("Scrittura del token su " + tokenFile + " fallita", e);
        }
        LOG.info("Registrazione completata: token salvato in {} (dispositivo {})", tokenFile, registrazione.get("deviceId"));
    }

    /**
     * Legge il token conservato nel file locale.
     *
     * @param properties proprietà di configurazione
     * @return l'oggetto {@link java.util.Optional} prodotto o recuperato
     * @see java.util.Optional
     *  
     */
    public static java.util.Optional<String> tokenPersistito(EdgeProperties properties) {
        Path tokenFile = tokenFilePath(properties);
        if (!Files.exists(tokenFile)) {
            return java.util.Optional.empty();
        }
        try {
            String token = Files.readString(tokenFile).trim();
            return token.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(token);
        } catch (IOException e) {
            throw new IllegalStateException("Lettura del token da " + tokenFile + " fallita", e);
        }
    }
}
