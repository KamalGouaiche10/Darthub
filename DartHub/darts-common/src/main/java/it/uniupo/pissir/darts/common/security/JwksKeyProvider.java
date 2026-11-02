package it.uniupo.pissir.darts.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;

/**
 * Recupera le chiavi pubbliche necessarie alla verifica dei JWT. 
 */
public final class JwksKeyProvider {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 2000;
    private static final int SIZE_LIMIT_BYTES = 50_000;
    private static final int MAX_ATTEMPTS = 10;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private JwksKeyProvider() {
    }

    /**
     * Recupera le chiavi pubbliche dall'endpoint JWKS o dalla configurazione alternativa.
     *
     * @param jwksUrl indirizzo dell'endpoint delle chiavi pubbliche
     * @param staticJwkFallback insieme di chiavi pubbliche configurato come alternativa
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.RsaKeyMaterial} prodotto o recuperato
     * @throws IllegalStateException se i dati o lo stato del componente non consentono l'operazione
     * @see it.uniupo.pissir.darts.common.security.RsaKeyMaterial
     *  
     */
    public static RsaKeyMaterial fetch(String jwksUrl, String staticJwkFallback) {
        JOSEException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JWKSet jwkSet = JWKSet.load(URI.create(jwksUrl).toURL(), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, SIZE_LIMIT_BYTES);
                return toKeyMaterial(firstRsaKey(jwkSet, jwksUrl));
            } catch (IOException | ParseException | IllegalArgumentException e) {
                sleepBeforeRetry(attempt);
            } catch (JOSEException e) {
                lastError = e;
                break;
            }
        }
        if (staticJwkFallback != null && !staticJwkFallback.isBlank()) {
            try {
                return toKeyMaterial(RSAKey.parse(staticJwkFallback));
            } catch (ParseException | JOSEException e) {
                throw new IllegalStateException("JWK statico di fallback non valido", e);
            }
        }
        throw new IllegalStateException(
                "Impossibile recuperare la chiave pubblica JWT da " + jwksUrl
                        + " dopo " + MAX_ATTEMPTS + " tentativi e nessun fallback statico configurato",
                lastError);
    }

    /**
     * Seleziona la prima chiave RSA disponibile.
     *
     * @param jwkSet insieme delle chiavi JWK
     * @param jwksUrl indirizzo dell'endpoint delle chiavi pubbliche
     * @return l'oggetto {@link com.nimbusds.jose.jwk.RSAKey} prodotto o recuperato
     * @throws IllegalStateException se viene rilevato l'errore «Nessuna chiave RSA nella JWKS di»
     * @see com.nimbusds.jose.jwk.RSAKey
     *  
     */
    private static RSAKey firstRsaKey(JWKSet jwkSet, String jwksUrl) {
        List<JWK> keys = jwkSet.getKeys();
        return keys.stream()
                .filter(RSAKey.class::isInstance)
                .map(RSAKey.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nessuna chiave RSA nella JWKS di " + jwksUrl));
    }

    /**
     * Converte la chiave RSA nel materiale crittografico utilizzato dai token.
     *
     * @param rsaKey chiave RSA da utilizzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.RsaKeyMaterial} prodotto o recuperato
     * @throws JOSEException se l'operazione non può essere completata
     * @see it.uniupo.pissir.darts.common.security.RsaKeyMaterial
     *  
     */
    private static RsaKeyMaterial toKeyMaterial(RSAKey rsaKey) throws JOSEException {
        return new RsaKeyMaterial(rsaKey.toRSAPublicKey(), null, rsaKey.getKeyID());
    }

    /**
     * Attende prima di ripetere il tentativo.
     *
     * @param attempt numero del tentativo
     *  
     */
    private static void sleepBeforeRetry(int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrotto durante l'attesa della JWKS", ie);
        }
    }
}
