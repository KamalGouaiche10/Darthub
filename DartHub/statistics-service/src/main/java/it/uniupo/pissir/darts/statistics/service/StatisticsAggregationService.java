package it.uniupo.pissir.darts.statistics.service;

import it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO;
import it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO;
import it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO;
import it.uniupo.pissir.darts.common.event.GameEvent;
import it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Applica le regole e coordina le operazioni relative a statistics aggregation. 
 */
public final class StatisticsAggregationService {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsAggregationService.class);
    private static final String MODALITA_INDIVIDUALE = "INDIVIDUALE";

    private final ConnectionProvider connectionProvider;
    private final PlayerStatisticsDao playerStatisticsDao;
    private final TeamStatisticsDao teamStatisticsDao;
    private final LocaleStatisticsDao localeStatisticsDao;
    private final GameStatisticsDao gameStatisticsDao;
    private final GlobalStatisticsDao globalStatisticsDao;
    private final PlayerVisibilityDao playerVisibilityDao;
    private final LocaleVisibilityDao localeVisibilityDao;
    private final ProcessedEventDao processedEventDao;

    /**
     * Inizializza {@code StatisticsAggregationService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param playerStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao} utilizzato dall'operazione
     * @param teamStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao} utilizzato dall'operazione
     * @param localeStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao} utilizzato dall'operazione
     * @param gameStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao} utilizzato dall'operazione
     * @param globalStatisticsDao componente {@link it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao} utilizzato dall'operazione
     * @param playerVisibilityDao componente {@link it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao} utilizzato dall'operazione
     * @param localeVisibilityDao componente {@link it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao} utilizzato dall'operazione
     * @param processedEventDao componente {@link it.uniupo.pissir.darts.common.persistence.ProcessedEventDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public StatisticsAggregationService(ConnectionProvider connectionProvider, PlayerStatisticsDao playerStatisticsDao,
                                         TeamStatisticsDao teamStatisticsDao, LocaleStatisticsDao localeStatisticsDao,
                                         GameStatisticsDao gameStatisticsDao, GlobalStatisticsDao globalStatisticsDao,
                                         PlayerVisibilityDao playerVisibilityDao, LocaleVisibilityDao localeVisibilityDao,
                                         ProcessedEventDao processedEventDao) {
        this.connectionProvider = connectionProvider;
        this.playerStatisticsDao = playerStatisticsDao;
        this.teamStatisticsDao = teamStatisticsDao;
        this.localeStatisticsDao = localeStatisticsDao;
        this.gameStatisticsDao = gameStatisticsDao;
        this.globalStatisticsDao = globalStatisticsDao;
        this.playerVisibilityDao = playerVisibilityDao;
        this.localeVisibilityDao = localeVisibilityDao;
        this.processedEventDao = processedEventDao;
    }

    /**
     * Gestisce partita completata per il statistics aggregation.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.MatchCompletedEventDTO
     *  
     */
    public void gestisciPartitaCompletata(MatchCompletedEventDTO evento) {
        connectionProvider.withTransaction(connection -> {
            if (processedEventDao.giaElaborato(connection, evento.eventId())) {
                LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
                return null;
            }
            boolean individuale = MODALITA_INDIVIDUALE.equals(evento.modalita());
            Instant adesso = Instant.now();

            for (MatchCompletedEventDTO.Risultato risultato : evento.risultati()) {
                if (individuale && risultato.playerId() != null) {
                    playerStatisticsDao.applicaRisultatoPartita(connection, risultato.playerId(),
                            risultato.punteggioFinale(), risultato.vincitore(), adesso);
                } else if (!individuale && risultato.teamId() != null) {
                    teamStatisticsDao.applicaRisultatoPartita(connection, risultato.teamId(),
                            risultato.punteggioFinale(), risultato.vincitore(), adesso);
                }
                if (risultato.playerId() != null) {
                    localeStatisticsDao.registraGiocatore(connection, evento.localeId(), evento.dartboardId(),
                            risultato.playerId());
                }
            }
            localeStatisticsDao.registraPartita(connection, evento.localeId(), evento.dartboardId(), adesso);
            gameStatisticsDao.registraPartita(connection, evento.gameTypeId(), evento.risultati().size());
            globalStatisticsDao.incrementaPartiteTotali(connection, adesso);

            processedEventDao.segnaElaborato(connection, evento.eventId());
            return null;
        });
    }

    /**
     * Gestisce tiro per il statistics aggregation.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.event.GameEvent
     *  
     */
    public void gestisciTiro(GameEvent evento) {
        if (evento.playerId() == null) {
            return;
        }
        connectionProvider.withTransaction(connection -> {
            if (processedEventDao.giaElaborato(connection, evento.eventId())) {
                LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
                return null;
            }
            playerStatisticsDao.aggiornaMigliorTiro(connection, evento.playerId(), evento.punti(), Instant.now());
            processedEventDao.segnaElaborato(connection, evento.eventId());
            return null;
        });
    }

    /**
     * Gestisce torneo aggiornato per il statistics aggregation.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.TournamentUpdatedEventDTO
     *  
     */
    public void gestisciTorneoAggiornato(TournamentUpdatedEventDTO evento) {
        connectionProvider.withTransaction(connection -> {
            if (processedEventDao.giaElaborato(connection, evento.eventId())) {
                LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
                return null;
            }
            processedEventDao.segnaElaborato(connection, evento.eventId());
            return null;
        });
    }

    /**
     * Gestisce conto utente per il statistics aggregation.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.UserCountChangedEventDTO
     *  
     */
    public void gestisciContoUtente(UserCountChangedEventDTO evento) {
        connectionProvider.withTransaction(connection -> {
            if (processedEventDao.giaElaborato(connection, evento.eventId())) {
                LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
                return null;
            }
            globalStatisticsDao.applicaDeltaUtenti(connection, evento.conta() ? 1 : -1, Instant.now());
            playerVisibilityDao.setVisibile(connection, evento.userId(), evento.conta());
            processedEventDao.segnaElaborato(connection, evento.eventId());
            return null;
        });
    }

    /**
     * Gestisce conto locale per il statistics aggregation.
     *
     * @param evento evento da elaborare
     * @see it.uniupo.pissir.darts.common.dto.LocaleCountChangedEventDTO
     *  
     */
    public void gestisciContoLocale(LocaleCountChangedEventDTO evento) {
        connectionProvider.withTransaction(connection -> {
            if (processedEventDao.giaElaborato(connection, evento.eventId())) {
                LOG.debug("Evento {} già elaborato: scartato", evento.eventId());
                return null;
            }
            globalStatisticsDao.applicaDeltaLocali(connection, evento.conta() ? 1 : -1, Instant.now());
            localeVisibilityDao.setVisibile(connection, evento.localeId(), evento.conta());
            processedEventDao.segnaElaborato(connection, evento.eventId());
            return null;
        });
    }
}
