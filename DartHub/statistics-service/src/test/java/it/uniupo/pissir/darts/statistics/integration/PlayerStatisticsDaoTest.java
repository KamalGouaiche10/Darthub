package it.uniupo.pissir.darts.statistics.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.statistics.dao.PlayerStatisticsDao;
import it.uniupo.pissir.darts.statistics.model.PlayerStatistics;
import it.uniupo.pissir.darts.statistics.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica il comportamento di {@code PlayerStatisticsDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class PlayerStatisticsDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private PlayerStatisticsDao playerStatisticsDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        playerStatisticsDao = new PlayerStatisticsDao(connectionProvider);
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code applicaRisultatoPartita} nel caso prima volta crea la riga e ricalcola le medie. 
     */
    @Test
    void applicaRisultatoPartita_primaVolta_creaLaRigaERicalcolaLeMedie() {
        connectionProvider.withTransaction(connection -> {
            playerStatisticsDao.applicaRisultatoPartita(connection, 5L, 501, true, Instant.now());
            return null;
        });

        PlayerStatistics s = playerStatisticsDao.findById(5L).orElseThrow();
        assertEquals(1, s.partiteGiocate());
        assertEquals(1, s.vittorie());
        assertEquals(100.0, s.percentualeVittorie());
        assertEquals(501.0, s.punteggioMedio());
    }

    /**
     * Verifica {@code applicaRisultatoPartita} nel caso due volte percentuale e media ricalcolate dai totali. 
     */
    @Test
    void applicaRisultatoPartita_dueVolte_percentualeEMediaRicalcolateDaiTotali() {
        connectionProvider.withTransaction(connection -> {
            playerStatisticsDao.applicaRisultatoPartita(connection, 5L, 400, true, Instant.now());
            playerStatisticsDao.applicaRisultatoPartita(connection, 5L, 200, false, Instant.now());
            return null;
        });

        PlayerStatistics s = playerStatisticsDao.findById(5L).orElseThrow();
        assertEquals(2, s.partiteGiocate());
        assertEquals(1, s.vittorie());
        assertEquals(50.0, s.percentualeVittorie());
        assertEquals(300.0, s.punteggioMedio());
    }

    /**
     * Verifica {@code aggiornaMigliorTiro} nel caso tiene il massimo. 
     */
    @Test
    void aggiornaMigliorTiro_tieneIlMassimo() {
        connectionProvider.withTransaction(connection -> {
            playerStatisticsDao.aggiornaMigliorTiro(connection, 5L, 60, Instant.now());
            playerStatisticsDao.aggiornaMigliorTiro(connection, 5L, 41, Instant.now());
            playerStatisticsDao.aggiornaMigliorTiro(connection, 5L, 100, Instant.now());
            return null;
        });

        assertEquals(100, playerStatisticsDao.findById(5L).orElseThrow().migliorTiro());
    }

    /**
     * Verifica {@code classifica} nel caso ordinata per percentuale vittorie decrescente. 
     */
    @Test
    void classifica_ordinataPerPercentualeVittorieDecrescente() {
        connectionProvider.withTransaction(connection -> {
            playerStatisticsDao.applicaRisultatoPartita(connection, 5L, 100, false, Instant.now());
            playerStatisticsDao.applicaRisultatoPartita(connection, 6L, 100, true, Instant.now());
            return null;
        });

        List<PlayerStatistics> classifica = playerStatisticsDao.classifica(Page.of(0, 20), "percentualeVittorie");

        assertEquals(6L, classifica.get(0).playerId());
    }

    /**
     * Verifica {@code classifica} nel caso ordinata per punti totali. 
     */
    @Test
    void classifica_ordinataPerPuntiTotali() {
        connectionProvider.withTransaction(connection -> {
            playerStatisticsDao.applicaRisultatoPartita(connection, 5L, 100, true, Instant.now());
            playerStatisticsDao.applicaRisultatoPartita(connection, 6L, 900, true, Instant.now());
            return null;
        });

        List<PlayerStatistics> classifica = playerStatisticsDao.classifica(Page.of(0, 20), "puntiTotali");

        assertEquals(6L, classifica.get(0).playerId());
    }
}
