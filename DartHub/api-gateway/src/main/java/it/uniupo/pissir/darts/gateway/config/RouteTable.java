package it.uniupo.pissir.darts.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Associa i percorsi delle richieste ai servizi destinatari.
 *
 * @see java.util.Map
 *  
 */
public final class RouteTable {

    private final Map<String, String> prefissoPerBaseUrl = new LinkedHashMap<>();

    /**
     * Inizializza {@code RouteTable} con i dati e le dipendenze indicati.
     *
     * @param config configurazione del componente
     * @see it.uniupo.pissir.darts.gateway.config.GatewayConfig
     *  
     */
    public RouteTable(GatewayConfig config) {
        prefissoPerBaseUrl.put("auth", config.userServiceBaseUrl());
        prefissoPerBaseUrl.put("users", config.userServiceBaseUrl());
        prefissoPerBaseUrl.put("locali", config.localeServiceBaseUrl());
        prefissoPerBaseUrl.put("bersagli", config.localeServiceBaseUrl());
        prefissoPerBaseUrl.put("edge-devices", config.localeServiceBaseUrl());
        prefissoPerBaseUrl.put("giochi", config.gameServiceBaseUrl());
        prefissoPerBaseUrl.put("sensori", config.gameServiceBaseUrl());
        prefissoPerBaseUrl.put("regole", config.gameServiceBaseUrl());
        prefissoPerBaseUrl.put("partite", config.matchServiceBaseUrl());
        prefissoPerBaseUrl.put("squadre", config.matchServiceBaseUrl());
        prefissoPerBaseUrl.put("tornei", config.tournamentServiceBaseUrl());
        prefissoPerBaseUrl.put("statistiche", config.statisticsServiceBaseUrl());
    }

    /**
     * Individua il destinatario o il risultato corrispondente ai dati ricevuti.
     *
     * @param path percorso o indirizzo della risorsa
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<String> risolvi(String path) {
        String[] segmenti = path.split("/", 3);
        if (segmenti.length < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(prefissoPerBaseUrl.get(segmenti[1]));
    }
}
