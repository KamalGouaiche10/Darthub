package it.uniupo.pissir.darts.game.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dao.ScoringRuleDao;
import it.uniupo.pissir.darts.game.dto.ScoringRuleDTO;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.service.GameTypeService;
import it.uniupo.pissir.darts.game.service.ScoringRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code ScoringRuleService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.dao.ScoringRuleDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class ScoringRuleServiceTest {

    @Mock
    private ScoringRuleDao scoringRuleDao;
    @Mock
    private GameTypeDao gameTypeDao;

    private ScoringRuleService scoringRuleService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        GameTypeService gameTypeService = new GameTypeService(gameTypeDao);
        scoringRuleService = new ScoringRuleService(scoringRuleDao, gameTypeService);
        lenient().when(gameTypeDao.findById(1L)).thenReturn(Optional.of(new GameType(1L, "Freccette", null, "darts")));
    }

    /**
     * Verifica {@code create} nel caso gioco tipo inesistente non found. 
     */
    @Test
    void create_gameTypeInesistente_notFound() {
        when(gameTypeDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> scoringRuleService.create(99L, "501", "DECRESCENTE", 501, 0, true, true, 3, null));
    }

    /**
     * Verifica {@code create} nel caso variante vuota validation exception. 
     */
    @Test
    void create_varianteVuota_validationException() {
        assertThrows(ValidationException.class,
                () -> scoringRuleService.create(1L, " ", "DECRESCENTE", 501, 0, true, true, 3, null));
    }

    /**
     * Verifica {@code create} nel caso direzione non valida validation exception. 
     */
    @Test
    void create_direzioneNonValida_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> scoringRuleService.create(1L, "501", "LATERALE", 501, 0, true, true, 3, null));
        assertEquals("DIREZIONE_NON_VALIDA", e.codice());
    }

    /**
     * Verifica {@code create} nel caso max tiri non positivo validation exception. 
     */
    @Test
    void create_maxTiriNonPositivo_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> scoringRuleService.create(1L, "501", "DECRESCENTE", 501, 0, true, true, 0, null));
        assertEquals("MAX_TIRI_NON_VALIDO", e.codice());
    }

    /**
     * Verifica {@code create} nel caso decrescente con iniziale minore di obiettivo validation exception. 
     */
    @Test
    void create_decrescenteConInizialeMinoreDiObiettivo_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> scoringRuleService.create(1L, "501", "DECRESCENTE", 10, 50, true, true, 3, null));
        assertEquals("PUNTEGGI_INCOERENTI", e.codice());
    }

    /**
     * Verifica {@code create} nel caso crescente con iniziale maggiore di obiettivo validation exception. 
     */
    @Test
    void create_crescenteConInizialeMaggioreDiObiettivo_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> scoringRuleService.create(1L, "cricket", "CRESCENTE", 50, 10, false, true, 3, null));
        assertEquals("PUNTEGGI_INCOERENTI", e.codice());
    }

    /**
     * Verifica {@code create} nel caso valida applica predefinito ed inserisce. 
     */
    @Test
    void create_valida_applicaDefaultEdInserisce() {
        when(scoringRuleDao.insert(any(ScoringRule.class))).thenAnswer(inv -> {
            ScoringRule r = inv.getArgument(0);
            return new ScoringRule(1L, r.gameTypeId(), r.variante(), r.direzione(), r.punteggioIniziale(),
                    r.punteggioObiettivo(), r.chiusuraDouble(), r.bustAnnullaTurno(), r.maxTiriPerTurno(), r.parametriJson());
        });

        ScoringRuleDTO dto = scoringRuleService.create(1L, "501", null, null, null, null, null, null, null);

        assertEquals("DECRESCENTE", dto.direzione());
        assertEquals(3, dto.maxTiriPerTurno());
        assertEquals(true, dto.bustAnnullaTurno());
        assertEquals("{}", dto.parametriJson());
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti e valida coerenza. 
     */
    @Test
    void update_campiParziali_mantengonoIRestantiEValidaCoerenza() {
        ScoringRule current = new ScoringRule(1L, 1L, "501", Direzione.DECRESCENTE, 501, 0, true, true, 3, "{}");
        when(scoringRuleDao.findById(1L)).thenReturn(Optional.of(current));

        ValidationException e = assertThrows(ValidationException.class,
                () -> scoringRuleService.update(1L, null, null, 0, 100, null, null, null, null));
        assertEquals("PUNTEGGI_INCOERENTI", e.codice());
    }

    /**
     * Verifica {@code requireScoringRule} nel caso inesistente non found. 
     */
    @Test
    void requireScoringRule_inesistente_notFound() {
        when(scoringRuleDao.findById(99L)).thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> scoringRuleService.requireScoringRule(99L));
        assertEquals("REGOLA_NON_TROVATA", e.codice());
    }
}
