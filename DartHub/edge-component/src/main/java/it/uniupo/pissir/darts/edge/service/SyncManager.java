package it.uniupo.pissir.darts.edge.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import it.uniupo.pissir.darts.edge.config.EdgeProperties;
import it.uniupo.pissir.darts.edge.dao.BufferedThrowDao;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.BufferedThrow;
import it.uniupo.pissir.darts.edge.model.ConnectionState;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.LocalParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sincronizza tiri e partite locali quando il sistema centrale è raggiungibile. 
 */
public final class SyncManager {

    private static final Logger LOG = LoggerFactory.getLogger(SyncManager.class);
    private static final Gson GSON = GsonFactory.instance();

    private final LocalMatchDao localMatchDao;
    private final BufferedThrowDao bufferedThrowDao;
    private final EdgeProperties properties;
    private final ConnectionMonitor connectionMonitor;
    private final HttpClient httpClient;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-manager");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger tentativiFalliti = new AtomicInteger(0);
    private final AtomicLong clockOffsetMs = new AtomicLong(0);

    /**
     * Inizializza {@code SyncManager} con i dati e le dipendenze indicati.
     *
     * @param localMatchDao componente {@link it.uniupo.pissir.darts.edge.dao.LocalMatchDao} utilizzato dall'operazione
     * @param bufferedThrowDao componente {@link it.uniupo.pissir.darts.edge.dao.BufferedThrowDao} utilizzato dall'operazione
     * @param properties proprietà di configurazione
     * @param connectionMonitor componente {@link it.uniupo.pissir.darts.edge.service.ConnectionMonitor} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.edge.dao.LocalMatchDao
     *  
     */
    public SyncManager(LocalMatchDao localMatchDao, BufferedThrowDao bufferedThrowDao, EdgeProperties properties,
                        ConnectionMonitor connectionMonitor) {
        this.localMatchDao = localMatchDao;
        this.bufferedThrowDao = bufferedThrowDao;
        this.properties = properties;
        this.connectionMonitor = connectionMonitor;
        this.httpClient = createHttpClient(properties);
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        connectionMonitor.addListener(stato -> {
            if (stato == ConnectionState.ONLINE) {
                LOG.info("Broker centrale riconnesso: avvio la sincronizzazione");
                executor.execute(this::tentaSincronizzazioneECicla);
            }
        });
        executor.schedule(this::tentaSincronizzazioneECicla, properties.syncRetryBaseSecondi(), TimeUnit.SECONDS);
    }

    /**
     * Esegue i tentativi di sincronizzazione finché il componente è attivo. 
     */
    private void tentaSincronizzazioneECicla() {
        boolean successo;
        try {
            successo = sincronizzaTutto();
        } catch (RuntimeException e) {
            LOG.error("Ciclo di sincronizzazione fallito; i dati restano nel buffer per il prossimo tentativo", e);
            successo = false;
        }
        if (successo) {
            tentativiFalliti.set(0);
        } else {
            tentativiFalliti.incrementAndGet();
        }
        long ritardo = Math.min(
                (long) properties.syncRetryBaseSecondi() << Math.min(tentativiFalliti.get(), 6),
                properties.syncRetryMaxSecondi());
        executor.schedule(this::tentaSincronizzazioneECicla, ritardo, TimeUnit.SECONDS);
    }

    /**
     * Invia al sistema centrale i dati locali in attesa di sincronizzazione.
     *
     * @return l'esito della condizione verificata
     *  
     */
    public boolean sincronizzaTutto() {
        if (!connectionMonitor.isOnline()) {
            return true;
        }
        boolean tuttoOk = true;
        for (LocalMatch match : localMatchDao.findConTiriDaSincronizzare()) {
            tuttoOk &= syncOffline(match);
        }
        for (LocalMatch match : localMatchDao.findDaImportare()) {
            tuttoOk &= importOffline(match);
        }
        return tuttoOk;
    }

    /**
     * Riconcilia con il server i tiri conservati durante la disconnessione.
     *
     * @param match partita locale da elaborare
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    private boolean syncOffline(LocalMatch match) {
        List<BufferedThrow> pendenti = bufferedThrowDao.findPendentiByMatch(match.localUuid());
        if (pendenti.isEmpty() || match.serverId() == null) {
            return true;
        }
        List<Map<String, Object>> tiri = new ArrayList<>();
        for (BufferedThrow t : pendenti) {
            Long serverParticipantId = localMatchDao.findParticipantById(t.localParticipantId())
                    .map(LocalParticipant::serverParticipantId).orElse(null);
            if (serverParticipantId == null) {
                LOG.warn("Tiro {} senza partecipante server: sincronizzazione rinviata", t.id());
                return false;
            }
            tiri.add(Map.of(
                    "participantId", serverParticipantId,
                    "settore", t.settore().name(),
                    "moltiplicatore", t.moltiplicatore().name(),
                    "timestampSensore", t.timestampSensore().toString()));
        }
        Map<String, Object> body = Map.of("dartboardId", match.dartboardId(), "matchId", match.serverId(),
                "clockOffsetMs", clockOffsetMs.get(), "tiri", tiri);

        try {
            HttpResponse<String> response = invia(properties.gatewayUrl() + "/partite/sync-offline", body);
            if (response.statusCode() / 100 != 2) {
                LOG.warn("sync-offline per {} fallita: HTTP {} — {}", match.localUuid(), response.statusCode(), response.body());
                return false;
            }
            aggiornaClockOffset(response);
            for (BufferedThrow t : pendenti) {
                bufferedThrowDao.markSincronizzato(t.id());
            }
            LOG.info("sync-offline per {}: {} tiri sincronizzati", match.localUuid(), pendenti.size());
            return true;
        } catch (Exception e) {
            LOG.warn("sync-offline per {} fallita: {}", match.localUuid(), e.getMessage());
            return false;
        }
    }

    /**
     * Importa sul server una partita creata localmente.
     *
     * @param match partita locale da elaborare
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    private boolean importOffline(LocalMatch match) {
        List<LocalParticipant> partecipanti = localMatchDao.findParticipantsByMatch(match.localUuid());
        List<BufferedThrow> tuttiTiri = bufferedThrowDao.findByMatch(match.localUuid());
        Map<Long, Integer> ordinePerLocalId = new HashMap<>();
        List<Map<String, Object>> partecipantiJson = new ArrayList<>();
        for (LocalParticipant p : partecipanti) {
            ordinePerLocalId.put(p.id(), p.ordineTurno());
            Map<String, Object> pm = new HashMap<>();
            pm.put("playerId", p.playerId());
            pm.put("nomeVisualizzato", p.nomeVisualizzato());
            pm.put("ordineTurno", p.ordineTurno());
            partecipantiJson.add(pm);
        }
        List<Map<String, Object>> tiriJson = new ArrayList<>();
        for (BufferedThrow t : tuttiTiri) {
            tiriJson.add(Map.of(
                    "participantId", ordinePerLocalId.get(t.localParticipantId()),
                    "settore", t.settore().name(),
                    "moltiplicatore", t.moltiplicatore().name(),
                    "timestampSensore", t.timestampSensore().toString()));
        }

        JsonObject regola = GSON.fromJson(match.scoringRuleJson(), JsonObject.class);
        Map<String, Object> body = new HashMap<>();
        body.put("localUuid", match.localUuid());
        body.put("dartboardId", match.dartboardId());
        body.put("localeId", properties.localeId());
        body.put("gameTypeId", properties.gameTypeId());
        body.put("clockOffsetMs", clockOffsetMs.get());
        body.put("scoringRule", regola);
        body.put("partecipanti", partecipantiJson);
        body.put("tiri", tiriJson);

        try {
            HttpResponse<String> response = invia(properties.gatewayUrl() + "/partite/import-offline", body);
            if (response.statusCode() / 100 != 2) {
                LOG.warn("import-offline per {} fallita: HTTP {} — {}", match.localUuid(), response.statusCode(), response.body());
                return false;
            }
            aggiornaClockOffset(response);
            JsonObject risposta = GSON.fromJson(response.body(), JsonObject.class);
            long serverId = risposta.get("id").getAsLong();

            LocalMatch importata = new LocalMatch(match.localUuid(), serverId, match.dartboardId(),
                    match.scoringRuleJson(), match.origine(), LocalMatchStatus.COMPLETATA, true, match.dataInizio(),
                    match.dataFine());
            localMatchDao.update(importata);
            LOG.info("import-offline per {} riuscita: server_id={}", match.localUuid(), serverId);
            return true;
        } catch (Exception e) {
            LOG.warn("import-offline per {} fallita: {}", match.localUuid(), e.getMessage());
            return false;
        }
    }

    /**
     * Prepara il client HTTP con le opzioni di connessione e sicurezza.
     *
     * @param properties proprietà di configurazione
     * @return l'oggetto {@link java.net.http.HttpClient} prodotto o recuperato
     * @see java.net.http.HttpClient
     *  
     */
    private static HttpClient createHttpClient(EdgeProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (properties.gatewayTrustStorePath().isBlank()) {
            return builder.build();
        }
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(properties.gatewayTrustStorePath()))) {
                trustStore.load(input, properties.gatewayTrustStorePassword().toCharArray());
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers.getTrustManagers(), null);
            return builder.sslContext(sslContext).build();
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("Truststore del gateway non valido", e);
        }
    }

    /**
     * Inoltra la richiesta al destinatario e restituisce la risposta.
     *
     * @param url indirizzo della risorsa HTTP
     * @param body corpo della richiesta o della risposta
     * @return la risposta ricevuta dal servizio destinatario
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     * @see java.net.http.HttpResponse
     *  
     */
    private HttpResponse<String> invia(String url, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.tokenEdgeDevice())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Aggiorna clock offset per il sync manager.
     *
     * @param response risposta ricevuta dal servizio
     * @see java.net.http.HttpResponse
     *  
     */
    private void aggiornaClockOffset(HttpResponse<String> response) {
        response.headers().firstValue("Date").ifPresent(dateHeader -> {
            try {
                Instant serverTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateHeader));
                clockOffsetMs.set(serverTime.toEpochMilli() - Instant.now().toEpochMilli());
            } catch (RuntimeException e) {
                LOG.debug("Header Date non interpretabile: {}", dateHeader);
            }
        });
    }
}
