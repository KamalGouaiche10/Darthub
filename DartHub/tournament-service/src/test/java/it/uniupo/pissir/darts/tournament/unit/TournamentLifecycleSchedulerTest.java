package it.uniupo.pissir.darts.tournament.unit;

import it.uniupo.pissir.darts.common.exception.BusinessRuleException;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.TournamentLifecycleScheduler;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifica il comportamento di {@code TournamentLifecycleScheduler} con casi di prova JUnit. 
 */
class TournamentLifecycleSchedulerTest {
    /**
     * Verifica il caso avvia solo i scaduti e registra gli iscritti insufficienti. 
     */
    @Test
    void avviaSoloIScadutiERegistraGliIscrittiInsufficienti() {
        TournamentDao tournaments = mock(TournamentDao.class);
        ScheduleGenerator generator = mock(ScheduleGenerator.class);
        TournamentService service = mock(TournamentService.class);

        when(tournaments.findDueIds(any(Instant.class)))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of());
        when(generator.generaCalendario(1L, false)).thenReturn(List.of(30L));
        when(generator.generaCalendario(2L, false)).thenThrow(
                new BusinessRuleException("ISCRITTI_INSUFFICIENTI", "Servono due iscritti"));

        TournamentLifecycleScheduler scheduler = new TournamentLifecycleScheduler(tournaments, generator, service);
        scheduler.run();
        scheduler.run();

        verify(generator, times(1)).generaCalendario(1L, false);
        verify(generator, times(1)).generaCalendario(2L, false);
        verify(service).segnaInCorso(2L);
    }

    /**
     * Verifica il caso chiude i tornei scaduti ma aspetta le partite ancora aperte. 
     */
    @Test
    void chiudeITorneiScadutiMaAspettaLePartiteAncoraAperte() {
        TournamentDao tournaments = mock(TournamentDao.class);
        ScheduleGenerator generator = mock(ScheduleGenerator.class);
        TournamentService service = mock(TournamentService.class);
        when(tournaments.findDueToCloseIds(any(Instant.class))).thenReturn(List.of(3L, 4L));
        when(service.chiudiScaduto(eq(3L), any(Instant.class))).thenThrow(new BusinessRuleException(
                "PARTITE_NON_COMPLETATE", "Partite ancora aperte"));

        new TournamentLifecycleScheduler(tournaments, generator, service).run();

        verify(service).chiudiScaduto(eq(3L), any(Instant.class));
        verify(service).chiudiScaduto(eq(4L), any(Instant.class));
    }
}
