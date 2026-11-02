package it.uniupo.pissir.darts.game.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dao.ScoringRuleDao;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code ScoringRuleDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class ScoringRuleDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private ScoringRuleDao scoringRuleDao;
    private long gameTypeId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        GameTypeDao gameTypeDao = new GameTypeDao(connectionProvider);
        scoringRuleDao = new ScoringRuleDao(connectionProvider);
        gameTypeId = gameTypeDao.insert(new GameType(0, "Freccette", null, "darts")).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip. 
     */
    @Test
    void insertEFindById_roundTrip() {
        ScoringRule inserito = scoringRuleDao.insert(regola501());

        ScoringRule rileto = scoringRuleDao.findById(inserito.id()).orElseThrow();
        assertEquals("501", rileto.variante());
        assertEquals(Direzione.DECRESCENTE, rileto.direzione());
        assertTrue(rileto.chiusuraDouble());
    }

    /**
     * Verifica {@code insert} nel caso variante duplicata per lo stesso gioco conflict exception. 
     */
    @Test
    void insert_varianteDuplicataPerLoStessoGioco_conflictException() {
        scoringRuleDao.insert(regola501());

        ConflictException e = assertThrows(ConflictException.class, () -> scoringRuleDao.insert(regola501()));
        assertEquals("REGOLA_DUPLICATA", e.codice());
    }

    /**
     * Verifica il caso find per gioco tipo e conteggio per gioco tipo. 
     */
    @Test
    void findByGameTypeECountByGameType() {
        scoringRuleDao.insert(regola501());
        scoringRuleDao.insert(new ScoringRule(0, gameTypeId, "301", Direzione.DECRESCENTE, 301, 0, true, true, 3, "{}"));

        assertEquals(2, scoringRuleDao.findByGameType(gameTypeId, Page.of(0, 20)).size());
        assertEquals(2, scoringRuleDao.countByGameType(gameTypeId));
    }

    /**
     * Verifica {@code update} nel caso modifica parametri di gioco. 
     */
    @Test
    void update_modificaParametriDiGioco() {
        ScoringRule inserito = scoringRuleDao.insert(regola501());
        ScoringRule modificata = new ScoringRule(inserito.id(), gameTypeId, "501", Direzione.DECRESCENTE, 501, 0,
                false, false, 5, "{\"x\":1}");

        scoringRuleDao.update(modificata);

        ScoringRule rileta = scoringRuleDao.findById(inserito.id()).orElseThrow();
        assertEquals(false, rileta.chiusuraDouble());
        assertEquals(5, rileta.maxTiriPerTurno());
    }

    /**
     * Verifica {@code delete} nel caso locale cascada sul gioco. 
     */
    @Test
    void delete_locale_cascadaSulGioco() {
        scoringRuleDao.insert(regola501());
        GameTypeDao gameTypeDao = new GameTypeDao(connectionProvider);

        gameTypeDao.delete(gameTypeId);

        assertTrue(scoringRuleDao.findByGameType(gameTypeId, Page.of(0, 20)).isEmpty());
    }

    /**
     * Prepara la regola 501 usata nei test.
     *
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    private ScoringRule regola501() {
        return new ScoringRule(0, gameTypeId, "501", Direzione.DECRESCENTE, 501, 0, true, true, 3, "{}");
    }
}
