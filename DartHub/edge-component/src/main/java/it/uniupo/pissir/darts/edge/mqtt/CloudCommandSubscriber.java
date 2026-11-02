package it.uniupo.pissir.darts.edge.mqtt;

import com.google.gson.Gson;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchOrigin;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.RemoteCommand;
import it.uniupo.pissir.darts.edge.model.RosterEntry;
import it.uniupo.pissir.darts.edge.service.MatchSessionManager;
import it.uniupo.pissir.darts.edge.service.RuleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Riceve ed elabora i messaggi MQTT relativi a cloud command. 
 */
public final class CloudCommandSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(CloudCommandSubscriber.class);
    private static final int QOS_COMANDI = 2;
    private static final Gson GSON = GsonFactory.instance();

    private final MqttEventBus eventBusCentrale;
    private final long localeId;
    private final long dartboardId;
    private final LocalMatchDao localMatchDao;
    private final RuleCache ruleCache;
    private final MatchSessionManager matchSessionManager;

    /**
     * Inizializza {@code CloudCommandSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBusCentrale bus MQTT del broker centrale
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param localMatchDao componente {@link it.uniupo.pissir.darts.edge.dao.LocalMatchDao} utilizzato dall'operazione
     * @param ruleCache componente {@link it.uniupo.pissir.darts.edge.service.RuleCache} utilizzato dall'operazione
     * @param matchSessionManager partita session manager da utilizzare
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public CloudCommandSubscriber(MqttEventBus eventBusCentrale, long localeId, long dartboardId,
                                   LocalMatchDao localMatchDao, RuleCache ruleCache,
                                   MatchSessionManager matchSessionManager) {
        this.eventBusCentrale = eventBusCentrale;
        this.localeId = localeId;
        this.dartboardId = dartboardId;
        this.localMatchDao = localMatchDao;
        this.ruleCache = ruleCache;
        this.matchSessionManager = matchSessionManager;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    public void start() {
        if (eventBusCentrale == null) {
            LOG.warn("MQTT centrale non disponibile: CloudCommandSubscriber non sottoscritto");
            return;
        }
        String topic = MqttTopics.giochiComandi(localeId, dartboardId);
        eventBusCentrale.subscribe(topic, QOS_COMANDI, RemoteCommand.class, (t, comando) -> gestisci(comando));
        LOG.info("Sottoscritto a {}", topic);
    }

    /**
     * Applica all'edge il comando ricevuto dal sistema centrale.
     *
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.edge.model.RemoteCommand
     *  
     */
    private void gestisci(RemoteCommand comando) {
        try {
            switch (comando.comando()) {
                case "START_MATCH" -> avviaPartita(comando);
                case "END_MATCH" -> chiudiPartita(comando);
                default -> LOG.warn("Comando non riconosciuto ignorato: {}", comando.comando());
            }
        } catch (RuntimeException e) {
            LOG.error("Gestione del comando {} fallita: {}", comando.comando(), e.getMessage(), e);
        }
    }

    /**
     * Avvia partita per il cloud command.
     *
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.edge.model.RemoteCommand
     *  
     */
    private void avviaPartita(RemoteCommand comando) {
        if (localMatchDao.findByServerId(comando.matchId()).isPresent()) {
            LOG.info("START_MATCH per la partita {} già registrata localmente: comando duplicato ignorato",
                    comando.matchId());
            return;
        }
        if (localMatchDao.findInCorso().isPresent()) {
            LOG.warn("START_MATCH {} ignorato: il bersaglio sta già giocando un'altra partita", comando.matchId());
            return;
        }
        String scoringRuleJson = GSON.toJson(comando.scoringRule());
        String rosterJson = GSON.toJson(comando.roster());
        ruleCache.save(null, scoringRuleJson, rosterJson);

        String localUuid = java.util.UUID.randomUUID().toString();
        LocalMatch match = new LocalMatch(localUuid, comando.matchId(), dartboardId, scoringRuleJson,
                LocalMatchOrigin.SERVER, LocalMatchStatus.IN_CORSO, true, Instant.now(), null);
        List<RosterEntry> roster = comando.roster() == null ? List.of() : comando.roster();

        localMatchDao.insertMatchWithRoster(match, roster, comando.scoringRule().punteggioIniziale());
        LOG.info("START_MATCH ricevuto: partita server {} avviata localmente ({} partecipanti)",
                comando.matchId(), roster.size());
    }

    /**
     * Chiude partita per il cloud command.
     *
     * @param comando comando da applicare al dispositivo
     * @see it.uniupo.pissir.darts.edge.model.RemoteCommand
     *  
     */
    private void chiudiPartita(RemoteCommand comando) {
        if (matchSessionManager.comandoObsoleto(comando.matchId())) {
            LOG.info("END_MATCH per la partita {} già chiusa localmente: comando in ritardo ignorato", comando.matchId());
            return;
        }
        if (localMatchDao.findByServerId(comando.matchId()).isEmpty()) {
            LOG.warn("END_MATCH per la partita {} senza corrispondenza locale: ignorato", comando.matchId());
            return;
        }
        if (matchSessionManager.partitaInCorso()
                .map(m -> m.serverId() == null || m.serverId() != comando.matchId()).orElse(true)) {
            LOG.info("END_MATCH {} ignorato: non è la partita corrente del bersaglio", comando.matchId());
            return;
        }
        matchSessionManager.chiudiPartitaInCorso();
        LOG.info("END_MATCH ricevuto: partita server {} chiusa localmente", comando.matchId());
    }
}
