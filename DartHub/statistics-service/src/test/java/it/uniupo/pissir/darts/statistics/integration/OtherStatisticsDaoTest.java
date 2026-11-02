package it.uniupo.pissir.darts.statistics.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.statistics.dao.GameStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.GlobalStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleStatisticsDao;
import it.uniupo.pissir.darts.statistics.dao.LocaleVisibilityDao;
import it.uniupo.pissir.darts.statistics.dao.PlayerVisibilityDao;
import it.uniupo.pissir.darts.common.persistence.ProcessedEventDao;
import it.uniupo.pissir.darts.statistics.dao.TeamStatisticsDao;
import it.uniupo.pissir.darts.statistics.model.GameStatistics;
import it.uniupo.pissir.darts.statistics.model.GlobalStatistics;
import it.uniupo.pissir.darts.statistics.model.LocaleStatistics;
import it.uniupo.pissir.darts.statistics.model.TeamStatistics;
import it.uniupo.pissir.darts.statistics.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code OtherStatisticsDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class OtherStatisticsDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private TeamStatisticsDao teamStatisticsDao;
    private LocaleStatisticsDao localeStatisticsDao;
    private GameStatisticsDao gameStatisticsDao;
    private GlobalStatisticsDao globalStatisticsDao;
    private PlayerVisibilityDao playerVisibilityDao;
    private LocaleVisibilityDao localeVisibilityDao;
    private ProcessedEventDao processedEventDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        teamStatisticsDao = new TeamStatisticsDao(connectionProvider);
        localeStatisticsDao = new LocaleStatisticsDao(connectionProvider);
        gameStatisticsDao = new GameStatisticsDao(connectionProvider);
        globalStatisticsDao = new GlobalStatisticsDao(connectionProvider);
        playerVisibilityDao = new PlayerVisibilityDao(connectionProvider);
        localeVisibilityDao = new LocaleVisibilityDao(connectionProvider);
        processedEventDao = new ProcessedEventDao();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code teamStatistics} nel caso applica risultato partita ricalcola percentuale e media. 
     */
    @Test
    void teamStatistics_applicaRisultatoPartita_ricalcolaPercentualeEMedia() {
        connectionProvider.withTransaction(connection -> {
            teamStatisticsDao.applicaRisultatoPartita(connection, 20L, 501, true, Instant.now());
            teamStatisticsDao.applicaRisultatoPartita(connection, 20L, 300, false, Instant.now());
            return null;
        });

        TeamStatistics s = teamStatisticsDao.findById(20L).orElseThrow();
        assertEquals(2, s.partiteGiocate());
        assertEquals(50.0, s.percentualeVittorie());
        assertEquals(400.5, s.punteggioMedio());
    }

    /**
     * Verifica {@code localeStatistics} nel caso registra partita e giocatore distinti senza duplicati. 
     */
    @Test
    void localeStatistics_registraPartitaEGiocatore_distintiSenzaDuplicati() {
        connectionProvider.withTransaction(connection -> {
            localeStatisticsDao.registraPartita(connection, 9L, 10L, Instant.now());
            localeStatisticsDao.registraGiocatore(connection, 9L, 10L, 5L);
            localeStatisticsDao.registraGiocatore(connection, 9L, 10L, 5L);
            localeStatisticsDao.registraGiocatore(connection, 9L, 10L, 6L);
            return null;
        });

        LocaleStatistics s = localeStatisticsDao.findByDartboard(10L).orElseThrow();
        assertEquals(1, s.numeroPartite());
        assertEquals(2, s.giocatoriDistinti());

        LocaleStatistics aggregato = localeStatisticsDao.findByLocale(9L).orElseThrow();
        assertEquals(1, aggregato.numeroPartite());
    }

    /**
     * Verifica {@code gameStatistics} nel caso registra partita media giocatori incrementale. 
     */
    @Test
    void gameStatistics_registraPartita_mediaGiocatoriIncrementale() {
        connectionProvider.withTransaction(connection -> {
            gameStatisticsDao.registraPartita(connection, 1L, 2);
            gameStatisticsDao.registraPartita(connection, 1L, 4);
            return null;
        });

        GameStatistics s = gameStatisticsDao.findById(1L).orElseThrow();
        assertEquals(2, s.partiteTotali());
        assertEquals(3.0, s.mediaGiocatoriPerPartita());
    }

    /**
     * Verifica {@code globalStatistics} nel caso incrementa partite totali. 
     */
    @Test
    void globalStatistics_incrementaPartiteTotali() {
        connectionProvider.withTransaction(connection -> {
            globalStatisticsDao.incrementaPartiteTotali(connection, Instant.now());
            globalStatisticsDao.incrementaPartiteTotali(connection, Instant.now());
            return null;
        });

        assertEquals(2, globalStatisticsDao.find().orElseThrow().partiteTotali());
    }

    /**
     * Verifica {@code globalStatistics} nel caso applica delta utenti clampata a zero. 
     */
    @Test
    void globalStatistics_applicaDeltaUtenti_clampataAZero() {
        connectionProvider.withTransaction(connection -> {
            globalStatisticsDao.applicaDeltaUtenti(connection, -1, Instant.now());
            return null;
        });

        GlobalStatistics s = globalStatisticsDao.find().orElseThrow();
        assertEquals(0, s.utentiTotali());
    }

    /**
     * Verifica {@code globalStatistics} nel caso applica delta utenti incrementa e decrementa. 
     */
    @Test
    void globalStatistics_applicaDeltaUtenti_incrementaEDecrementa() {
        connectionProvider.withTransaction(connection -> {
            globalStatisticsDao.applicaDeltaUtenti(connection, 1, Instant.now());
            globalStatisticsDao.applicaDeltaUtenti(connection, 1, Instant.now());
            globalStatisticsDao.applicaDeltaUtenti(connection, -1, Instant.now());
            return null;
        });

        assertEquals(1, globalStatisticsDao.find().orElseThrow().utentiTotali());
    }

    /**
     * Verifica {@code playerVisibility} nel caso predefinito visibile poi bloccato. 
     */
    @Test
    void playerVisibility_defaultVisibile_poiBloccato() {
        assertTrue(playerVisibilityDao.isVisibile(5L));

        connectionProvider.withTransaction(connection -> {
            playerVisibilityDao.setVisibile(connection, 5L, false);
            return null;
        });

        assertFalse(playerVisibilityDao.isVisibile(5L));
    }

    /**
     * Verifica {@code localeVisibility} nel caso predefinito visibile poi nascosto. 
     */
    @Test
    void localeVisibility_defaultVisibile_poiNascosto() {
        assertTrue(localeVisibilityDao.isVisibile(9L));

        connectionProvider.withTransaction(connection -> {
            localeVisibilityDao.setVisibile(connection, 9L, false);
            return null;
        });

        assertFalse(localeVisibilityDao.isVisibile(9L));
    }

    /**
     * Verifica {@code processedEvent} nel caso idempotenza. 
     */
    @Test
    void processedEvent_idempotenza() {
        connectionProvider.withTransaction(connection -> {
            assertFalse(processedEventDao.giaElaborato(connection, "evt-1"));
            processedEventDao.segnaElaborato(connection, "evt-1");
            assertTrue(processedEventDao.giaElaborato(connection, "evt-1"));
            return null;
        });
    }
}
