package it.uniupo.pissir.darts.tournament.service;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Gestisce i dati e le operazioni di torneo lifecycle scheduler. 
 */
public final class TournamentLifecycleScheduler implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TournamentLifecycleScheduler.class);

    private final TournamentDao tournaments;
    private final ScheduleGenerator generator;
    private final TournamentService tournamentService;

    /**
     * Inizializza {@code TournamentLifecycleScheduler} con i dati e le dipendenze indicati.
     *
     * @param tournaments tornei da utilizzare
     * @param generator generator da utilizzare
     * @param tournamentService componente {@link it.uniupo.pissir.darts.tournament.service.TournamentService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.tournament.dao.TournamentDao
     *  
     */
    public TournamentLifecycleScheduler(TournamentDao tournaments, ScheduleGenerator generator,
                                        TournamentService tournamentService) {
        this.tournaments = tournaments;
        this.generator = generator;
        this.tournamentService = tournamentService;
    }

    /**
     * Avvia l'elaborazione del componente. 
     */
    @Override
    public void run() {
        try {
            Instant now = Instant.now();
            for (Long id : tournaments.findDueToCloseIds(now)) {
                try {
                    tournamentService.chiudiScaduto(id, now);
                } catch (BusinessRuleException e) {
                    if (!"PARTITE_NON_COMPLETATE".equals(e.codice()))
                        LOG.warn("Chiusura torneo {}: {}", id, e.getMessage());
                } catch (RuntimeException e) {
                    LOG.warn("Chiusura torneo {}: {}", id, e.getMessage());
                }
            }
            for (Long id : tournaments.findDueIds(now)) {
                try {
                    generator.generaCalendario(id, false);
                } catch (BusinessRuleException e) {
                    if ("ISCRITTI_INSUFFICIENTI".equals(e.codice())) tournamentService.segnaInCorso(id);
                    else LOG.warn("Calendario torneo {}: {}", id, e.getMessage());
                } catch (RuntimeException e) {
                    LOG.warn("Calendario torneo {}: {}", id, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Ricerca tornei in scadenza fallita: {}", e.getMessage());
        }
    }
}
