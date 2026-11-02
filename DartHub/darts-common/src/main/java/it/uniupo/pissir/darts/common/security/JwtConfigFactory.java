package it.uniupo.pissir.darts.common.security;

import java.util.Properties;

/**
 * Prepara le configurazioni JWT per la firma e per la verifica dei token. 
 */
public final class JwtConfigFactory {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private JwtConfigFactory() {
    }

    /**
     * Prepara la configurazione per verificare i token.
     *
     * @param properties proprietà di configurazione
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.JwtConfig} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.JwtConfig
     *  
     */
    public static JwtConfig forVerifier(Properties properties) {
        String jwksUrl = require(properties, "jwt.jwksUrl");
        String staticFallback = properties.getProperty("jwt.publicKeyJwk");
        RsaKeyMaterial keyMaterial = JwksKeyProvider.fetch(jwksUrl, staticFallback);
        return new JwtConfig(keyMaterial, properties);
    }

    /**
     * Verifica il requisito  prima di eseguire l'operazione.
     *
     * @param properties proprietà di configurazione
     * @param key chiave da cercare o utilizzare
     * @return il testo o il valore di configurazione richiesto
     * @throws IllegalArgumentException se i dati o lo stato del componente non consentono l'operazione
     * @see java.util.Properties
     *  
     */
    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Proprietà obbligatoria mancante: " + key);
        }
        return value;
    }
}
