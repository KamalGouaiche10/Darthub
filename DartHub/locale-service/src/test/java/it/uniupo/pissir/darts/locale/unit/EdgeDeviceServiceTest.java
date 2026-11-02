package it.uniupo.pissir.darts.locale.unit;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.http.ServiceHttpClient;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.dto.RegisterEdgeDeviceRequestDTO;
import it.uniupo.pissir.darts.locale.model.EdgeConfig;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.mqtt.EdgeConfigPublisher;
import it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher;
import it.uniupo.pissir.darts.locale.service.DartboardService;
import it.uniupo.pissir.darts.locale.service.EdgeDeviceService;
import it.uniupo.pissir.darts.locale.service.LocaleService;
import it.uniupo.pissir.darts.locale.support.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica il comportamento di {@code EdgeDeviceService} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
@ExtendWith(MockitoExtension.class)
class EdgeDeviceServiceTest {

    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private EdgeDeviceDao edgeDeviceDao;
    @Mock
    private DartboardDao dartboardDao;
    @Mock
    private LocaleDao localeDao;
    @Mock
    private EdgeConfigPublisher edgeConfigPublisher;
    @Mock
    private ServiceHttpClient httpClient;
    @Mock
    private LocaleEventPublisher localeEventPublisher;

    private EdgeDeviceService edgeDeviceService;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        Mocks.passThroughTransactionsAndConnections(connectionProvider);
        LocaleService localeService = new LocaleService(localeDao, localeEventPublisher);
        DartboardService dartboardService = new DartboardService(dartboardDao, localeService);
        edgeDeviceService = new EdgeDeviceService(connectionProvider, edgeDeviceDao, dartboardService,
                edgeConfigPublisher, httpClient, "http://localhost:7001");
    }

    /**
     * Verifica {@code register} nel caso bersaglio identificativo mancante validation exception. 
     */
    @Test
    void register_dartboardIdMancante_validationException() {
        RegisterEdgeDeviceRequestDTO body = new RegisterEdgeDeviceRequestDTO(null, "SN1", null, null, null, null);

        ValidationException e = assertThrows(ValidationException.class, () -> edgeDeviceService.register(body));
        assertEquals("CAMPO_MANCANTE", e.codice());
    }

    /**
     * Verifica {@code register} nel caso serial number mancante validation exception. 
     */
    @Test
    void register_serialNumberMancante_validationException() {
        RegisterEdgeDeviceRequestDTO body = new RegisterEdgeDeviceRequestDTO(1L, " ", null, null, null, null);

        assertThrows(ValidationException.class, () -> edgeDeviceService.register(body));
    }

    /**
     * Verifica {@code register} nel caso bersaglio inesistente non found. 
     */
    @Test
    void register_dartboardInesistente_notFound() {
        when(dartboardDao.findById(99L)).thenReturn(Optional.empty());
        RegisterEdgeDeviceRequestDTO body = new RegisterEdgeDeviceRequestDTO(99L, "SN1", null, null, null, null);

        assertThrows(NotFoundException.class, () -> edgeDeviceService.register(body));
    }

    /**
     * Verifica {@code ownerOf} nel caso risale fino al proprietario del locale. 
     */
    @Test
    void ownerOf_risaleFinoAlProprietarioDelLocale() {
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(edgeDevice(7L, 10L, 1L, 5L)));
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(
                new it.uniupo.pissir.darts.locale.model.Dartboard(10L, 1L, "B1", 5L,
                        it.uniupo.pissir.darts.locale.model.DartboardStatus.DISPONIBILE, Instant.now())));
        when(localeDao.findById(1L)).thenReturn(Optional.of(locale(1L, 3L)));

        assertEquals(3L, edgeDeviceService.ownerOf(7L));
    }

    /**
     * Verifica {@code revoke} nel caso inesistente non found. 
     */
    @Test
    void revoke_inesistente_notFound() {
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> edgeDeviceService.revoke(7L));
    }

    /**
     * Verifica {@code revoke} nel caso con device utente identificativo revoca su utente servizio e delete bersaglio. 
     */
    @Test
    void revoke_conDeviceUserId_revocaSuUserServiceEDeleteBersaglio() {
        EdgeDevice device = edgeDevice(7L, 10L, 1L, 5L);
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(device));

        edgeDeviceService.revoke(7L);

        verify(httpClient).delete("http://localhost:7001/users/device-token/" + device.deviceUserId(), null);
        verify(edgeDeviceDao).delete(7L);
    }

    /**
     * Verifica {@code revoke} nel caso senza device utente identificativo non chiama utente servizio. 
     */
    @Test
    void revoke_senzaDeviceUserId_nonChiamaUserService() {
        EdgeDevice device = new EdgeDevice(7L, 10L, 1L, "SN1", 30, 90, 2, null, null, 0, EdgeDeviceStatus.OFFLINE, null);
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(device));

        edgeDeviceService.revoke(7L);

        verify(httpClient, never()).delete(anyString(), any());
        verify(edgeDeviceDao).delete(7L);
    }

    /**
     * Verifica {@code revoke} nel caso utente servizio fallisce illegal state exception e non cancella. 
     */
    @Test
    void revoke_userServiceFallisce_illegalStateExceptionENonCancella() {
        EdgeDevice device = edgeDevice(7L, 10L, 1L, 5L);
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(device));
        doThrow(new RuntimeException("user-service irraggiungibile")).when(httpClient).delete(anyString(), any());

        assertThrows(IllegalStateException.class, () -> edgeDeviceService.revoke(7L));
        verify(edgeDeviceDao, never()).delete(7L);
    }

    /**
     * Verifica {@code updateConfig} nel caso aggiorna e pubblica. 
     */
    @Test
    void updateConfig_aggiornaEPubblica() {
        EdgeDevice device = edgeDevice(7L, 10L, 1L, 5L);
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(device));
        EdgeConfig nuovaConfig = new EdgeConfig(60, 120, 1, "tcp://nuovo:1883");

        edgeDeviceService.updateConfig(7L, nuovaConfig);

        ArgumentCaptor<EdgeDevice> captor = ArgumentCaptor.forClass(EdgeDevice.class);
        verify(edgeDeviceDao).update(captor.capture());
        assertEquals(60, captor.getValue().intervalloHeartbeatS());
        verify(edgeConfigPublisher).publish(device.localeId(), device.dartboardId(), nuovaConfig);
    }

    /**
     * Verifica {@code updateConfig} nel caso non valida non scrive e non pubblica. 
     */
    @Test
    void updateConfig_nonValidaNonScriveENonPubblica() {
        assertThrows(it.uniupo.pissir.darts.common.exception.ValidationException.class,
                () -> edgeDeviceService.updateConfig(7L, new EdgeConfig(30, 10, 1, null)));
        assertThrows(it.uniupo.pissir.darts.common.exception.ValidationException.class,
                () -> edgeDeviceService.updateConfig(7L, new EdgeConfig(10, 30, 1, "http://localhost")));
        verify(edgeDeviceDao, never()).update(any(EdgeDevice.class));
        verify(edgeConfigPublisher, never()).publish(anyLong(), anyLong(), any());
    }

    /**
     * Verifica {@code status} nel caso riflette stato corrente. 
     */
    @Test
    void status_rifletteStatoCorrente() {
        EdgeDevice device = edgeDevice(7L, 10L, 1L, 5L);
        when(edgeDeviceDao.findById(7L)).thenReturn(Optional.of(device));

        var status = edgeDeviceService.status(7L);

        assertEquals("OFFLINE", status.stato());
        assertFalse(status.stato().equals("ONLINE"));
    }

    /**
     * Verifica {@code reserveDartboard} nel caso rifiuta segnale di presenza scaduto. 
     */
    @Test
    void reserveDartboard_rifiutaHeartbeatScaduto() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(new it.uniupo.pissir.darts.locale.model.Dartboard(
                10L, 1L, "B1", 5L, it.uniupo.pissir.darts.locale.model.DartboardStatus.DISPONIBILE, Instant.now())));
        when(edgeDeviceDao.findByDartboardId(10L)).thenReturn(Optional.of(new EdgeDevice(
                7L, 10L, 1L, "SN1", 30, 90, 2, null, Instant.now().minusSeconds(120), 0,
                EdgeDeviceStatus.ONLINE, 5L)));

        assertThrows(ConflictException.class, () -> edgeDeviceService.reserveDartboard(10L, 1L, 5L));
        verify(dartboardDao, never()).markInUseIfAvailable(10L);
    }

    /**
     * Verifica {@code reserveDartboard} nel caso richiede stato disponibile. 
     */
    @Test
    void reserveDartboard_richiedeStatoDisponibile() {
        when(dartboardDao.findById(10L)).thenReturn(Optional.of(new it.uniupo.pissir.darts.locale.model.Dartboard(
                10L, 1L, "B1", 5L, it.uniupo.pissir.darts.locale.model.DartboardStatus.DISPONIBILE, Instant.now())));
        when(edgeDeviceDao.findByDartboardId(10L)).thenReturn(Optional.of(new EdgeDevice(
                7L, 10L, 1L, "SN1", 30, 90, 2, null, Instant.now(), 0,
                EdgeDeviceStatus.ONLINE, 5L)));
        when(dartboardDao.markInUseIfAvailable(10L)).thenReturn(false);

        assertThrows(ConflictException.class, () -> edgeDeviceService.reserveDartboard(10L, 1L, 5L));
    }

    /**
     * Prepara il dispositivo edge con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del dispositivo edge
     * @param dartboardId identificativo del bersaglio
     * @param localeId identificativo del locale
     * @param deviceUserId identificativo dell'account tecnico del dispositivo
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.EdgeDevice} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.EdgeDevice
     *  
     */
    private static EdgeDevice edgeDevice(long id, long dartboardId, long localeId, long deviceUserId) {
        return new EdgeDevice(id, dartboardId, localeId, "SN1", 30, 90, 2, null, null, 0,
                EdgeDeviceStatus.OFFLINE, deviceUserId);
    }

    /**
     * Prepara il locale con i dati richiesti dal caso di prova.
     *
     * @param id identificativo del dispositivo edge
     * @param ownerUserId identificativo del proprietario del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    private static Locale locale(long id, long ownerUserId) {
        return new Locale(id, "Nome", "Via Test", LocaleType.PUBBLICO, ownerUserId, LocaleStatus.APPROVATO, Instant.now());
    }
}
