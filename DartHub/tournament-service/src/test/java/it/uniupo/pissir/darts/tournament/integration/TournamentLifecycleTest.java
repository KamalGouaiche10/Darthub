package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.StandingDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentMatchDao;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.service.GameTypeClient;
import it.uniupo.pissir.darts.tournament.service.LocaleClient;
import it.uniupo.pissir.darts.tournament.service.MatchServiceClient;
import it.uniupo.pissir.darts.tournament.service.ScheduleGenerator;
import it.uniupo.pissir.darts.tournament.service.TournamentLifecycleScheduler;
import it.uniupo.pissir.darts.tournament.service.TournamentService;
import it.uniupo.pissir.darts.tournament.support.TestDatabaseSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifica il comportamento di {@code TournamentLifecycle} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class TournamentLifecycleTest {
    @TempDir
    Path tempDir;

    /**
     * Verifica {@code scadenzaSenzaLocaliOPartite} nel caso persistita anche dopo un fermo del servizio. 
     */
    @Test
    void scadenzaSenzaLocaliOPartite_persistitaAncheDopoUnFermoDelServizio() {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(tempDir)) {
            TournamentDao dao = new TournamentDao(db);
            MatchServiceClient remote = mock(MatchServiceClient.class);
            ScheduleGenerator generator = mock(ScheduleGenerator.class);
            TournamentService service = new TournamentService(dao, new TournamentLocaleDao(db),
                    new TournamentMatchDao(db), new StandingDao(db), mock(GameTypeClient.class),
                    mock(LocaleClient.class), remote);
            Instant now = Instant.now();
            Tournament inCorso = dao.insert(torneo("champions league", TournamentStatus.IN_CORSO,
                    now.minusSeconds(3600), now.minusSeconds(1800)));
            Tournament maiPartito = dao.insert(torneo("Mai partito", TournamentStatus.APERTO,
                    now.minusSeconds(3600), now.minusSeconds(1800)));
            Tournament futuro = dao.insert(torneo("Futuro", TournamentStatus.APERTO,
                    now.plusSeconds(3600), now.plusSeconds(7200)));
            Tournament nonScaduto = dao.insert(torneo("In corso", TournamentStatus.IN_CORSO,
                    now.minusSeconds(3600), now.plusSeconds(7200)));
            TournamentLifecycleScheduler scheduler = new TournamentLifecycleScheduler(dao, generator, service);

            scheduler.run();

            for (Tournament originale : new Tournament[]{inCorso, maiPartito}) {
                Tournament chiuso = dao.findById(originale.id()).orElseThrow();
                assertEquals(TournamentStatus.CHIUSO, chiuso.stato());
                assertNull(chiuso.vincitoreParticipantId());
                assertNotNull(chiuso.dataFineEffettiva());
                assertEquals(originale.dataFine(), chiuso.dataFine());
                assertEquals(originale.dataInizioEffettiva(), chiuso.dataInizioEffettiva());
            }
            assertEquals(TournamentStatus.APERTO, dao.findById(futuro.id()).orElseThrow().stato());
            assertEquals(TournamentStatus.IN_CORSO, dao.findById(nonScaduto.id()).orElseThrow().stato());
            Instant fineEffettiva = dao.findById(inCorso.id()).orElseThrow().dataFineEffettiva();
            scheduler.run();
            assertEquals(fineEffettiva, dao.findById(inCorso.id()).orElseThrow().dataFineEffettiva());
            verifyNoInteractions(generator, remote);
        }
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param nome nome della risorsa
     * @param stato stato da impostare o verificare
     * @param inizio inizio dell'intervallo
     * @param fine fine dell'intervallo
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament torneo(String nome, TournamentStatus stato, Instant inizio, Instant fine) {
        return new Tournament(0, nome, 1L, ModalitaTorneo.INDIVIDUALE, FormulaTorneo.GIRONE,
                3, 1, 0, inizio, fine, stato, null,
                stato == TournamentStatus.IN_CORSO ? inizio : null, null);
    }
}
