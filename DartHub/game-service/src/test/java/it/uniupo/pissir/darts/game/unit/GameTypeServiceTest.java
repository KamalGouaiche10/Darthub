package it.uniupo.pissir.darts.game.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dto.GameTypeDTO;
import it.uniupo.pissir.darts.game.model.GameType;
import it.uniupo.pissir.darts.game.service.GameTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code GameTypeService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.game.dao.GameTypeDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class GameTypeServiceTest {

    @Mock
    private GameTypeDao gameTypeDao;

    private GameTypeService gameTypeService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        gameTypeService = new GameTypeService(gameTypeDao);
    }

    /**
     * Verifica {@code create} nel caso motore omesso predefinito darts. 
     */
    @Test
    void create_motoreOmesso_defaultDarts() {
        when(gameTypeDao.insert(any(GameType.class)))
                .thenAnswer(inv -> new GameType(1L, "Freccette", "Gioco delle freccette", "darts"));

        gameTypeService.create("Freccette", "Gioco delle freccette", null);

        ArgumentCaptor<GameType> captor = ArgumentCaptor.forClass(GameType.class);
        verify(gameTypeDao).insert(captor.capture());
        assertEquals("darts", captor.getValue().motore());
    }

    /**
     * Verifica {@code create} nel caso motore esplicito usa quello indicato. 
     */
    @Test
    void create_motoreEsplicito_usaQuelloIndicato() {
        when(gameTypeDao.insert(any(GameType.class)))
                .thenAnswer(inv -> new GameType(1L, "Altro", null, "altro-motore"));

        gameTypeService.create("Altro", null, "altro-motore");

        ArgumentCaptor<GameType> captor = ArgumentCaptor.forClass(GameType.class);
        verify(gameTypeDao).insert(captor.capture());
        assertEquals("altro-motore", captor.getValue().motore());
    }

    /**
     * Verifica {@code create} nel caso nome vuoto validation exception. 
     */
    @Test
    void create_nomeVuoto_validationException() {
        assertThrows(ValidationException.class, () -> gameTypeService.create(" ", "desc", "darts"));
    }

    /**
     * Verifica {@code requireGameType} nel caso inesistente non found. 
     */
    @Test
    void requireGameType_inesistente_notFound() {
        when(gameTypeDao.findById(99L)).thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> gameTypeService.requireGameType(99L));
        assertEquals("GIOCO_NON_TROVATO", e.codice());
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti. 
     */
    @Test
    void update_campiParziali_mantengonoIRestanti() {
        when(gameTypeDao.findById(1L)).thenReturn(Optional.of(new GameType(1L, "Vecchio", "Vecchia desc", "darts")));

        gameTypeService.update(1L, null, "Nuova desc", null);

        ArgumentCaptor<GameType> captor = ArgumentCaptor.forClass(GameType.class);
        verify(gameTypeDao).update(captor.capture());
        assertEquals("Vecchio", captor.getValue().nome());
        assertEquals("Nuova desc", captor.getValue().descrizione());
        assertEquals("darts", captor.getValue().motore());
    }

    /**
     * Verifica {@code delete} nel caso inesistente non found non chiama il dao. 
     */
    @Test
    void delete_inesistente_notFound_nonChiamaIlDao() {
        when(gameTypeDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gameTypeService.delete(99L));
    }

    /**
     * Verifica {@code findByIdDto} nel caso mappa tutti i campi. 
     */
    @Test
    void findByIdDto_mappaTuttiICampi() {
        when(gameTypeDao.findById(1L)).thenReturn(Optional.of(new GameType(1L, "Freccette", "Desc", "darts")));

        GameTypeDTO dto = gameTypeService.findByIdDto(1L);

        assertEquals(1L, dto.id());
        assertEquals("Freccette", dto.nome());
        assertEquals("Desc", dto.descrizione());
        assertEquals("darts", dto.motore());
    }
}
