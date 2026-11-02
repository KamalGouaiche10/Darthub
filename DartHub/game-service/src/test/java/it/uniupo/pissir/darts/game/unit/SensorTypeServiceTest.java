package it.uniupo.pissir.darts.game.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dao.SensorTypeDao;
import it.uniupo.pissir.darts.game.dto.SensorTypeDTO;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.model.SensorModalita;
import it.uniupo.pissir.darts.game.model.SensorType;
import it.uniupo.pissir.darts.game.service.GameTypeService;
import it.uniupo.pissir.darts.game.service.SensorTypeService;
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
 * Verifica il comportamento di {@code SensorTypeService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.dao.SensorTypeDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class SensorTypeServiceTest {

    @Mock
    private SensorTypeDao sensorTypeDao;
    @Mock
    private GameTypeDao gameTypeDao;

    private SensorTypeService sensorTypeService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        GameTypeService gameTypeService = new GameTypeService(gameTypeDao);
        sensorTypeService = new SensorTypeService(sensorTypeDao, gameTypeService);
        lenient().when(gameTypeDao.findById(1L)).thenReturn(Optional.of(new GameType(1L, "Freccette", null, "darts")));
    }

    /**
     * Verifica {@code create} nel caso gioco tipo inesistente non found. 
     */
    @Test
    void create_gameTypeInesistente_notFound() {
        when(gameTypeDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> sensorTypeService.create(99L, "Sensore", "{settore,moltiplicatore}", "REALE"));
    }

    /**
     * Verifica {@code create} nel caso nome vuoto validation exception. 
     */
    @Test
    void create_nomeVuoto_validationException() {
        assertThrows(ValidationException.class,
                () -> sensorTypeService.create(1L, " ", "{settore}", "REALE"));
    }

    /**
     * Verifica {@code create} nel caso formato dato vuoto validation exception. 
     */
    @Test
    void create_formatoDatoVuoto_validationException() {
        assertThrows(ValidationException.class,
                () -> sensorTypeService.create(1L, "Sensore", " ", "REALE"));
    }

    /**
     * Verifica {@code create} nel caso modalita non valida validation exception. 
     */
    @Test
    void create_modalitaNonValida_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> sensorTypeService.create(1L, "Sensore", "{settore}", "IPOTETICA"));
        assertEquals("MODALITA_NON_VALIDA", e.codice());
    }

    /**
     * Verifica {@code create} nel caso valido crea sensore. 
     */
    @Test
    void create_valido_creaSensore() {
        when(sensorTypeDao.insert(any(SensorType.class))).thenAnswer(inv -> {
            SensorType s = inv.getArgument(0);
            return new SensorType(5L, s.gameTypeId(), s.nome(), s.formatoDato(), s.modalita());
        });

        SensorTypeDTO dto = sensorTypeService.create(1L, "Sensore punteggio", "{settore,moltiplicatore}", "EMULATO");

        assertEquals("EMULATO", dto.modalita());
        assertEquals(1L, dto.gameTypeId());
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti. 
     */
    @Test
    void update_campiParziali_mantengonoIRestanti() {
        SensorType current = new SensorType(5L, 1L, "Vecchio", "{a}", SensorModalita.REALE);
        when(sensorTypeDao.findById(5L)).thenReturn(Optional.of(current));

        sensorTypeService.update(5L, null, null, "EMULATO");

        org.mockito.ArgumentCaptor<SensorType> captor = org.mockito.ArgumentCaptor.forClass(SensorType.class);
        org.mockito.Mockito.verify(sensorTypeDao).update(captor.capture());
        assertEquals("Vecchio", captor.getValue().nome());
        assertEquals("{a}", captor.getValue().formatoDato());
        assertEquals(SensorModalita.EMULATO, captor.getValue().modalita());
    }

    /**
     * Verifica {@code requireSensorType} nel caso inesistente non found. 
     */
    @Test
    void requireSensorType_inesistente_notFound() {
        when(sensorTypeDao.findById(99L)).thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> sensorTypeService.requireSensorType(99L));
        assertEquals("SENSORE_NON_TROVATO", e.codice());
    }
}
