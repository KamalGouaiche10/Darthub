package it.uniupo.pissir.darts.edge.mqtt;

import it.uniupo.pissir.darts.common.mqtt.MqttEventBus;
import it.uniupo.pissir.darts.common.mqtt.MqttTopics;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchOrigin;
import it.uniupo.pissir.darts.edge.model.LocalParticipant;
import it.uniupo.pissir.darts.edge.model.SensorEvent;
import it.uniupo.pissir.darts.edge.service.ConnectionMonitor;
import it.uniupo.pissir.darts.edge.service.MatchSessionManager;
import it.uniupo.pissir.darts.edge.service.MatchSessionManager.RisultatoTiro;
import it.uniupo.pissir.darts.edge.service.ScoreCalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Riceve ed elabora i messaggi MQTT relativi a locale sensore. 
 */
public final class LocalSensorSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(LocalSensorSubscriber.class);
    private final MqttEventBus eventBusLocale;
    private final long localeId;
    private final long dartboardId;
    private final ScoreCalculatorService scoreCalculatorService;
    private final MatchSessionManager matchSessionManager;
    private final LocalMatchDao localMatchDao;
    private final CloudPublisher cloudPublisher;
    private final ConnectionMonitor connectionMonitor;

    /**
     * Inizializza {@code LocalSensorSubscriber} con i dati e le dipendenze indicati.
     *
     * @param eventBusLocale bus MQTT del broker locale
     * @param localeId identificativo del locale
     * @param dartboardId identificativo del bersaglio
     * @param scoreCalculatorService componente {@link it.uniupo.pissir.darts.edge.service.ScoreCalculatorService} utilizzato dall'operazione
     * @param matchSessionManager partita session manager da utilizzare
     * @param localMatchDao componente {@link it.uniupo.pissir.darts.edge.dao.LocalMatchDao} utilizzato dall'operazione
     * @param cloudPublisher componente {@link it.uniupo.pissir.darts.edge.mqtt.CloudPublisher} utilizzato dall'operazione
     * @param connectionMonitor componente {@link it.uniupo.pissir.darts.edge.service.ConnectionMonitor} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.mqtt.MqttEventBus
     *  
     */
    public LocalSensorSubscriber(MqttEventBus eventBusLocale, long localeId, long dartboardId,
                                  ScoreCalculatorService scoreCalculatorService, MatchSessionManager matchSessionManager,
                                  LocalMatchDao localMatchDao, CloudPublisher cloudPublisher,
                                  ConnectionMonitor connectionMonitor) {
        this.eventBusLocale = eventBusLocale;
        this.localeId = localeId;
        this.dartboardId = dartboardId;
        this.scoreCalculatorService = scoreCalculatorService;
        this.matchSessionManager = matchSessionManager;
        this.localMatchDao = localMatchDao;
        this.cloudPublisher = cloudPublisher;
        this.connectionMonitor = connectionMonitor;
    }

    /**
     * Avvia l'elaborazione del componente.
     *
     * @param qos livello MQTT di qualità del servizio
     * @throws IllegalArgumentException se viene rilevato l'errore «QoS non valido»
     *  
     */
    public void start(int qos) {
        if (qos < 0 || qos > 2) throw new IllegalArgumentException("QoS non valido");
        if (eventBusLocale == null) {
            LOG.warn("MQTT locale non disponibile: LocalSensorSubscriber non sottoscritto");
            return;
        }
        String topic = MqttTopics.sensoreEvento(localeId, dartboardId);
        eventBusLocale.subscribe(topic, qos, SensorEvent.class, (t, evento) -> gestisci(evento));
        LOG.info("Sottoscritto a {}", topic);
    }

    /**
     * Applica il tiro del sensore alla partita locale e lo inoltra quando possibile.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.edge.model.SensorEvent
     *  
     */
    private void gestisci(SensorEvent evento) {
        try {
            Optional<LocalMatch> matchPrima = matchSessionManager.partitaInCorso();
            ScoreCalculatorService.TiroInterpretato tiro = scoreCalculatorService.interpreta(evento);
            Optional<RisultatoTiro> risultato = matchSessionManager.applicaTiro(tiro);
            if (risultato.isEmpty()) {
                return;
            }
            matchPrima.filter(m -> m.origine() == LocalMatchOrigin.SERVER && m.serverId() != null)
                    .filter(m -> connectionMonitor.isOnline())
                    .ifPresent(m -> rilanciaSulCloud(m, risultato.get(), tiro.timestampSensore()));
        } catch (RuntimeException e) {
            LOG.error("Gestione del tiro sensore fallita: {}", e.getMessage(), e);
        }
    }

    /**
     * Inoltra al server il tiro applicato alla partita locale.
     *
     * @param match partita locale da elaborare
     * @param risultato risultato da utilizzare
     * @param timestampSensore istante registrato dal sensore
     * @see it.uniupo.pissir.darts.edge.model.LocalMatch
     *  
     */
    private void rilanciaSulCloud(LocalMatch match, RisultatoTiro risultato, java.time.Instant timestampSensore) {
        Optional<LocalParticipant> partecipante = localMatchDao.findParticipantById(risultato.participantId());
        if (partecipante.isEmpty() || partecipante.get().serverParticipantId() == null) {
            LOG.warn("Partecipante locale {} senza server_participant_id: tiro non rilanciato dal vivo",
                    risultato.participantId());
            return;
        }
        cloudPublisher.pubblicaTiro(match.serverId(), partecipante.get().serverParticipantId(), risultato.settore(),
                risultato.moltiplicatore(), risultato.punti(), timestampSensore);
    }
}
