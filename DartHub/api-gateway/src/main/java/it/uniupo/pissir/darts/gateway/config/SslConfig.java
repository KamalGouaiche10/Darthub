package it.uniupo.pissir.darts.gateway.config;

import spark.Spark;

/**
 * Configura il listener HTTPS del gateway. 
 */
public final class SslConfig {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private SslConfig() {
    }

    /**
     * Configura il listener HTTPS del gateway.
     *
     * @param config configurazione del componente
     * @see it.uniupo.pissir.darts.gateway.config.GatewayConfig
     *  
     */
    public static void apply(GatewayConfig config) {
        if (config.localHttpEnabled()) {
            Spark.ipAddress("127.0.0.1");
            Spark.port(config.insecurePort());
            return;
        }
        Spark.port(config.securePort());
        Spark.secure(config.keystorePath(), config.keystorePassword(), null, null);
    }
}
