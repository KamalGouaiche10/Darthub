package it.uniupo.pissir.darts.locale.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.dto.LocaleDTO;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code LocaleService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.locale.dao.LocaleDao
 *  
 */
@ExtendWith(MockitoExtension.class)
class LocaleServiceTest {

    @Mock
    private LocaleDao localeDao;
    @Mock
    private LocaleEventPublisher localeEventPublisher;

    private LocaleService localeService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        localeService = new LocaleService(localeDao, localeEventPublisher);
    }

    /**
     * Verifica {@code create} nel caso approvato pubblica evento. 
     */
    @Test
    void create_approvato_pubblicaEvento() {
        Locale creato = locale(1L, "Bar Belvedere", 3L, LocaleStatus.APPROVATO);
        when(localeDao.insert(any(Locale.class))).thenReturn(creato);

        LocaleDTO dto = localeService.create("Bar Belvedere", "Via Roma", "PUBBLICO", 3L, LocaleStatus.APPROVATO);

        assertEquals("APPROVATO", dto.stato());
        verify(localeEventPublisher).publishConteggioCambiato(1L, true);
    }

    /**
     * Verifica {@code create} nel caso in attesa non pubblica evento. 
     */
    @Test
    void create_inAttesa_nonPubblicaEvento() {
        Locale creato = locale(2L, "Nuovo", 4L, LocaleStatus.IN_ATTESA);
        when(localeDao.insert(any(Locale.class))).thenReturn(creato);

        localeService.create("Nuovo", "Via X", "PRIVATO", 4L, LocaleStatus.IN_ATTESA);

        verify(localeEventPublisher, never()).publishConteggioCambiato(anyLong(), anyBoolean());
    }

    /**
     * Verifica {@code create} nel caso nome vuoto validation exception. 
     */
    @Test
    void create_nomeVuoto_validationException() {
        assertThrows(ValidationException.class,
                () -> localeService.create(" ", "Via X", "PUBBLICO", 1L, LocaleStatus.APPROVATO));
    }

    /**
     * Verifica {@code create} nel caso tipo non valido validation exception. 
     */
    @Test
    void create_tipoNonValido_validationException() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> localeService.create("Nome", "Via X", "FANTASIA", 1L, LocaleStatus.APPROVATO));
        assertEquals("TIPO_NON_VALIDO", e.codice());
    }

    /**
     * Verifica {@code requireLocale} nel caso inesistente non found. 
     */
    @Test
    void requireLocale_inesistente_notFound() {
        when(localeDao.findById(99L)).thenReturn(java.util.Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> localeService.requireLocale(99L));
        assertEquals("LOCALE_NON_TROVATO", e.codice());
    }

    /**
     * Verifica {@code update} nel caso campi parziali mantengono i restanti. 
     */
    @Test
    void update_campiParziali_mantengonoIRestanti() {
        Locale current = locale(1L, "Vecchio nome", 3L, LocaleStatus.APPROVATO);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.update(1L, null, "Nuovo indirizzo", null);

        org.mockito.ArgumentCaptor<Locale> captor = org.mockito.ArgumentCaptor.forClass(Locale.class);
        verify(localeDao).update(captor.capture());
        assertEquals("Vecchio nome", captor.getValue().nome());
        assertEquals("Nuovo indirizzo", captor.getValue().indirizzo());
        assertEquals(LocaleType.PUBBLICO, captor.getValue().tipo());
    }

    /**
     * Verifica {@code changeStato} nel caso da in attesa ad approvato pubblica conta vero. 
     */
    @Test
    void changeStato_daInAttesaAdApprovato_pubblicaContaTrue() {
        Locale current = locale(1L, "Nome", 3L, LocaleStatus.IN_ATTESA);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.changeStato(1L, LocaleStatus.APPROVATO);

        verify(localeEventPublisher).publishConteggioCambiato(1L, true);
    }

    /**
     * Verifica {@code changeStato} nel caso da in attesa a rifiutato non pubblica evento. 
     */
    @Test
    void changeStato_daInAttesaARifiutato_nonPubblicaEvento() {

        Locale current = locale(1L, "Nome", 3L, LocaleStatus.IN_ATTESA);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.changeStato(1L, LocaleStatus.RIFIUTATO);

        verify(localeEventPublisher, never()).publishConteggioCambiato(anyLong(), anyBoolean());
    }

    /**
     * Verifica {@code changeStato} nel caso da approvato a rifiutato pubblica conta falso. 
     */
    @Test
    void changeStato_daApprovatoARifiutato_pubblicaContaFalse() {
        Locale current = locale(1L, "Nome", 3L, LocaleStatus.APPROVATO);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.changeStato(1L, LocaleStatus.RIFIUTATO);

        verify(localeEventPublisher).publishConteggioCambiato(1L, false);
    }

    /**
     * Verifica {@code delete} nel caso locale approvato pubblica conta falso. 
     */
    @Test
    void delete_localeApprovato_pubblicaContaFalse() {
        Locale current = locale(1L, "Nome", 3L, LocaleStatus.APPROVATO);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.delete(1L);

        verify(localeEventPublisher).publishConteggioCambiato(1L, false);
        verify(localeDao).delete(1L);
    }

    /**
     * Verifica {@code delete} nel caso locale non approvato non pubblica evento. 
     */
    @Test
    void delete_localeNonApprovato_nonPubblicaEvento() {
        Locale current = locale(1L, "Nome", 3L, LocaleStatus.IN_ATTESA);
        when(localeDao.findById(1L)).thenReturn(java.util.Optional.of(current));

        localeService.delete(1L);

        verify(localeEventPublisher, never()).publishConteggioCambiato(anyLong(), anyBoolean());
    }

    /**
     * Verifica {@code search} nel caso calcola totale pagine dal dao. 
     */
    @Test
    void search_calcolaTotalePagineDalDao() {
        when(localeDao.search(eq(3L), eq(LocaleStatus.APPROVATO), any(Page.class)))
                .thenReturn(java.util.List.of(locale(1L, "A", 3L, LocaleStatus.APPROVATO)));
        when(localeDao.countSearch(3L, LocaleStatus.APPROVATO)).thenReturn(1L);

        var result = localeService.search(Page.of(0, 20), 3L, LocaleStatus.APPROVATO);

        assertEquals(1, result.contenuto().size());
        assertEquals(1L, result.totaleElementi());
    }

    /**
     * Prepara il locale con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del locale
     * @param nome nome della risorsa
     * @param ownerUserId identificativo del proprietario del locale
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    private static Locale locale(long id, String nome, long ownerUserId, LocaleStatus stato) {
        return new Locale(id, nome, "Via Test", LocaleType.PUBBLICO, ownerUserId, stato, Instant.now());
    }
}
