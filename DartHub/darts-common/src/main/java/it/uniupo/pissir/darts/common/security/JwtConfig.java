package it.uniupo.pissir.darts.common.security;

import org.pac4j.jwt.config.signature.RSASignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;

import java.time.Duration;
import java.util.Properties;

/**
 * Raccoglie chiavi, durate e parametri di configurazione dei JWT.
 *
 * @see it.uniupo.pissir.darts.common.security.RsaKeyMaterial
 *  
 */
public final class JwtConfig {

    private static final long DEFAULT_ACCESS_TOKEN_TTL_MINUTES = 30;
    private static final long DEFAULT_EDGE_DEVICE_TOKEN_TTL_DAYS = 365;

    private final RsaKeyMaterial keyMaterial;
    private final Duration accessTokenTtl;
    private final Duration edgeDeviceTokenTtl;

    /**
     * Inizializza {@code JwtConfig} con i dati e le dipendenze indicati.
     *
     * @param keyMaterial materiale crittografico per la firma o la verifica
     * @param properties proprietà di configurazione
     * @see it.uniupo.pissir.darts.common.security.RsaKeyMaterial
     *  
     */
    public JwtConfig(RsaKeyMaterial keyMaterial, Properties properties) {
        this.keyMaterial = keyMaterial;
        this.accessTokenTtl = Duration.ofMinutes(
                parseLong(properties.getProperty("jwt.accessTokenTtlMinutes"), DEFAULT_ACCESS_TOKEN_TTL_MINUTES));
        this.edgeDeviceTokenTtl = Duration.ofDays(
                parseLong(properties.getProperty("jwt.edgeDeviceTokenTtlDays"), DEFAULT_EDGE_DEVICE_TOKEN_TTL_DAYS));
    }

    /**
     * Restituisce la durata del token di accesso.
     *
     * @return durata del token di accesso
     * @see java.time.Duration
     *  
     */
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Restituisce la durata del token del dispositivo edge.
     *
     * @return durata del token del dispositivo edge
     * @see java.time.Duration
     *  
     */
    public Duration edgeDeviceTokenTtl() {
        return edgeDeviceTokenTtl;
    }

    /**
     * Restituisce la configurazione della firma JWT.
     *
     * @return configurazione della firma JWT
     * @see org.pac4j.jwt.config.signature.SignatureConfiguration
     *  
     */
    public SignatureConfiguration signatureConfiguration() {
        RSASignatureConfiguration signature = new RSASignatureConfiguration();
        signature.setPublicKey(keyMaterial.publicKey());
        if (keyMaterial.canSign()) {
            signature.setPrivateKey(keyMaterial.privateKey());
        }
        return signature;
    }

    /**
     * Legge e converte long dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @param fallback valore da usare come alternativa
     * @return il valore numerico calcolato o letto
     *  
     */
    private static long parseLong(String value, long fallback) {
        return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
    }
}
