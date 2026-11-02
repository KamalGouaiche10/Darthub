package it.uniupo.pissir.darts.locale.unit;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dto.DartboardDTO;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.DartboardStatus;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher;
import it.uniupo.pissir.darts.locale.service.DartboardService;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code DartboardService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.locale.dao.DartboardDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class DartboardServiceTest {

    @Mock
    private DartboardDao dartboardDao;
    @Mock
    private it.uniupo.pissir.darts.locale.dao.LocaleDao localeDao;
    @Mock
    private LocaleEventPublisher localeEventPublisher;

    private DartboardService dartboardService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        LocaleService localeService = new LocaleService(localeDao, localeEventPublisher);
        dartboardService = new DartboardService(dartboardDao, localeService);
    }

    /**
     * Verifica {@code create} nel caso gioco tipo identificativo mancante validation exception. 
     */
    @Test
    void create_gameTypeIdMancante_validationException() {
        when(localeDao.findById(1L)).thenReturn(Optional.of(locale(1L, 3L)));

        ValidationException e = assertThrows(ValidationException.class,
                () -> dartboardService.create(1L, "B1", null));
        assertEquals("CAMPO_MANCANTE", e.codice());
    }

    /**
     * Verifica {@code create} nel caso identificativo vuoto validation exception. 
     */
    @Test
    void create_identificativoVuoto_validationException() {
        when(localeDao.findById(1L)).thenReturn(Optional.of(locale(1L, 3L)));

        assertThrows(ValidationException.class, () -> dartboardService.create(1L, " ", 5L));
    }

    /**
     * Verifica {@code create} nel caso locale inesistente propaga non found. 
     */
    @Test
    void create_localeInesistente_propagaNotFound() {
        when(localeDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(it.uniupo.pissir.darts.common.exception.NotFoundException.class,
                () -> dartboardService.create(99L, "B1", 5L));
    }

    /**
     * Verifica {@code create} nel caso valido crea bersaglio disponibile. 
     */
    @Test
    void create_valido_creaBersaglioDisponibile() {
        when(localeDao.findById(1L)).thenReturn(Optional.of(locale(1L, 3L)));
        when(dartboardDao.insert(org.mockito.ArgumentMatchers.any(Dartboard.class)))
                .thenAnswer(inv -> new Dartboard(10L, 1L, "B1", 5L, DartboardStatus.DISPONIBILE, Instant.now()));

        DartboardDTO dto = dartboardService.create(1L, "B1", 5L);

        assertEquals("DISPONIBILE", dto.stato());
        assertEquals(5L, dto.gameTypeId());
    }

    /**
     * Verifica il caso prenotabile richiede edge online con segnale di presenza recente. 
     */
    @Test
    void prenotabileRichiedeEdgeOnlineConHeartbeatRecente() {
        EdgeDeviceDao edgeDao = org.mockito.Mockito.mock(EdgeDeviceDao.class);
        DartboardService service = new DartboardService(dartboardDao,
                new LocaleService(localeDao, localeEventPublisher), edgeDao);
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(dartboard(10L, 1L)));
        when(edgeDao.findByDartboardId(10L)).thenReturn(Optional.empty());
        assertFalse(service.findByIdDto(10L).prenotabile());

        when(edgeDao.findByDartboardId(10L)).thenReturn(Optional.of(new EdgeDevice(1, 10, 1,
                "SN", 30, 90, 2, null, Instant.now().minusSeconds(120), 0,
                EdgeDeviceStatus.ONLINE, 42L)));
        assertFalse(service.findByIdDto(10L).prenotabile());

        when(edgeDao.findByDartboardId(10L)).thenReturn(Optional.of(new EdgeDevice(1, 10, 1,
                "SN", 30, 90, 2, null, Instant.now(), 0, EdgeDeviceStatus.ONLINE, 42L)));
        assertTrue(service.findByIdDto(10L).prenotabile());
    }

    /**
     * Verifica {@code ownerOf} nel caso delega al proprietario del locale. 
     */
    @Test
    void ownerOf_delegaAlProprietarioDelLocale() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(dartboard(10L, 1L)));
        when(localeDao.findById(1L)).thenReturn(Optional.of(locale(1L, 3L)));

        long owner = dartboardService.ownerOf(10L);

        assertEquals(3L, owner);
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti. 
     */
    @Test
    void update_campiParziali_mantengonoIRestanti() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(dartboard(10L, 1L)));

        dartboardService.update(10L, null, 99L);

        ArgumentCaptor<Dartboard> captor = ArgumentCaptor.forClass(Dartboard.class);
        org.mockito.Mockito.verify(dartboardDao).update(captor.capture());
        assertEquals("B1", captor.getValue().identificativoLocale());
        assertEquals(99L, captor.getValue().gameTypeId());
    }

    /**
     * Verifica {@code setStato} nel caso valore non valido validation exception. 
     */
    @Test
    void setStato_valoreNonValido_validationException() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(dartboard(10L, 1L)));

        ValidationException e = assertThrows(ValidationException.class, () -> dartboardService.setStato(10L, "ROTTO"));
        assertEquals("STATO_NON_VALIDO", e.codice());
    }

    /**
     * Verifica {@code setStato} nel caso valido aggiorna stato. 
     */
    @Test
    void setStato_valido_aggiornaStato() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(dartboard(10L, 1L)));

        dartboardService.setStato(10L, "MANUTENZIONE");

        ArgumentCaptor<Dartboard> captor = ArgumentCaptor.forClass(Dartboard.class);
        org.mockito.Mockito.verify(dartboardDao).update(captor.capture());
        assertEquals(DartboardStatus.MANUTENZIONE, captor.getValue().stato());
    }

    /**
     * Prepara il locale con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del bersaglio
     * @param ownerUserId identificativo del proprietario del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    private static Locale locale(long id, long ownerUserId) {
        return new Locale(id, "Nome", "Via Test", LocaleType.PUBBLICO, ownerUserId, LocaleStatus.APPROVATO, Instant.now());
    }

    /**
     * Prepara il bersaglio con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del bersaglio
     * @param localeId identificativo del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Dartboard} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Dartboard
     *  
     */
    private static Dartboard dartboard(long id, long localeId) {
        return new Dartboard(id, localeId, "B1", 5L, DartboardStatus.DISPONIBILE, Instant.now());
    }
}
