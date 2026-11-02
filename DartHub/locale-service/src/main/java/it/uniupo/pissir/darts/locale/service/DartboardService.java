package it.uniupo.pissir.darts.locale.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao;
import it.uniupo.pissir.darts.locale.dto.DartboardDTO;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.DartboardStatus;
import it.uniupo.pissir.darts.locale.model.EdgeDevice;
import it.uniupo.pissir.darts.locale.model.EdgeDeviceStatus;

import java.time.Instant;

/**
 * Unicità dell'identificativo dentro il locale, CRUD dei bersagli.
 *
 * @see it.uniupo.pissir.darts.locale.dao.DartboardDao
 *  
 */
public final class DartboardService {

    private final DartboardDao dartboardDao;
    private final LocaleService localeService;
    private final EdgeDeviceDao edgeDeviceDao;

    /**
     * Inizializza {@code DartboardService} con i dati e le dipendenze indicati.
     *
     * @param dartboardDao componente {@link it.uniupo.pissir.darts.locale.dao.DartboardDao} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.dao.DartboardDao
     *  
     */
    public DartboardService(DartboardDao dartboardDao, LocaleService localeService) {
        this(dartboardDao, localeService, null);
    }

    /**
     * Inizializza {@code DartboardService} con i dati e le dipendenze indicati.
     *
     * @param dartboardDao componente {@link it.uniupo.pissir.darts.locale.dao.DartboardDao} utilizzato dall'operazione
     * @param localeService componente {@link it.uniupo.pissir.darts.locale.service.LocaleService} utilizzato dall'operazione
     * @param edgeDeviceDao componente {@link it.uniupo.pissir.darts.locale.dao.EdgeDeviceDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.dao.DartboardDao
     *  
     */
    public DartboardService(DartboardDao dartboardDao, LocaleService localeService, EdgeDeviceDao edgeDeviceDao) {
        this.dartboardDao = dartboardDao;
        this.localeService = localeService;
        this.edgeDeviceDao = edgeDeviceDao;
    }

    /**
     * Richiede che il bersaglio esista e sia accessibile.
     *
     * @param id identificativo del bersaglio
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Dartboard} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Bersaglio non trovato»
     * @see it.uniupo.pissir.darts.locale.model.Dartboard
     *  
     */
    public Dartboard requireDartboard(long id) {
        return dartboardDao.findById(id)
                .orElseThrow(() -> new NotFoundException("BERSAGLIO_NON_TROVATO", "Bersaglio non trovato"));
    }

    /**
     * Recupera l'identificativo del proprietario della risorsa.
     *
     * @param dartboardId identificativo del bersaglio
     * @return il valore numerico calcolato o letto
     *  
     */
    public long ownerOf(long dartboardId) {
        Dartboard dartboard = requireDartboard(dartboardId);
        return localeService.requireLocale(dartboard.localeId()).ownerUserId();
    }

    /**
     * Crea il bersaglio dopo la validazione dei dati.
     *
     * @param localeId identificativo del locale
     * @param identificativoLocale identificativo del bersaglio nel locale
     * @param gameTypeId identificativo del gioco
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.DartboardDTO} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «Campo obbligatorio mancante: gameTypeId»
     * @see it.uniupo.pissir.darts.locale.dto.DartboardDTO
     *  
     */
    public DartboardDTO create(long localeId, String identificativoLocale, Long gameTypeId) {
        localeService.requireLocale(localeId);
        requireNonBlank(identificativoLocale, "identificativoLocale");
        if (gameTypeId == null) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: gameTypeId");
        }
        Dartboard created = dartboardDao.insert(new Dartboard(0, localeId, identificativoLocale, gameTypeId,
                DartboardStatus.DISPONIBILE, Instant.now()));
        return toDto(created);
    }

    /**
     * Cerca bersagli per locale.
     *
     * @param localeId identificativo del locale
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<DartboardDTO> findByLocale(long localeId, Page page) {
        localeService.requireLocale(localeId);
        var list = dartboardDao.findByLocale(localeId, page).stream().map(this::toDto).toList();
        return PagedResult.of(list, page, dartboardDao.countByLocale(localeId));
    }

    /**
     * Cerca il bersaglio usando i dati della richiesta.
     *
     * @param id identificativo del bersaglio
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.DartboardDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.DartboardDTO
     *  
     */
    public DartboardDTO findByIdDto(long id) {
        return toDto(requireDartboard(id));
    }

    /**
     * Aggiorna il bersaglio dopo i controlli previsti.
     *
     * @param id identificativo del bersaglio
     * @param identificativoLocale identificativo del bersaglio nel locale
     * @param gameTypeId identificativo del gioco
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.DartboardDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.DartboardDTO
     *  
     */
    public DartboardDTO update(long id, String identificativoLocale, Long gameTypeId) {
        Dartboard current = requireDartboard(id);
        String newIdentificativo = identificativoLocale != null && !identificativoLocale.isBlank()
                ? identificativoLocale : current.identificativoLocale();
        long newGameTypeId = gameTypeId != null ? gameTypeId : current.gameTypeId();
        dartboardDao.update(new Dartboard(current.id(), current.localeId(), newIdentificativo, newGameTypeId,
                current.stato(), current.createdAt()));
        return findByIdDto(id);
    }

    /**
     * Imposta stato per il componente.
     *
     * @param id identificativo del bersaglio
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.DartboardDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.DartboardDTO
     *  
     */
    public DartboardDTO setStato(long id, String stato) {
        Dartboard current = requireDartboard(id);
        DartboardStatus parsed = parseStatus(stato);
        dartboardDao.update(new Dartboard(current.id(), current.localeId(), current.identificativoLocale(),
                current.gameTypeId(), parsed, current.createdAt()));
        return findByIdDto(id);
    }

    /**
     * Riserva il bersaglio per la partita.
     *
     * @param id identificativo del bersaglio
     * @throws ConflictException se viene rilevato l'errore «Il bersaglio non è disponibile»
     *  
     */
    public void reserve(long id) {
        if (!dartboardDao.markInUseIfAvailable(id)) {
            throw new ConflictException("BERSAGLIO_NON_DISPONIBILE", "Il bersaglio non è disponibile");
        }
    }

    /**
     * Rimuove il bersaglio verificando i vincoli dell'operazione.
     *
     * @param id identificativo del bersaglio
     *  
     */
    public void delete(long id) {
        requireDartboard(id);
        dartboardDao.delete(id);
    }

    /**
     * Legge e converte stato dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.DartboardStatus} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.DartboardStatus
     *  
     */
    private DartboardStatus parseStatus(String value) {
        try {
            return DartboardStatus.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("STATO_NON_VALIDO", "Stato non valido: " + value);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param dartboard bersaglio da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.DartboardDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.DartboardDTO
     *  
     */
    private DartboardDTO toDto(Dartboard dartboard) {
        EdgeDevice edge = edgeDeviceDao == null ? null : edgeDeviceDao.findByDartboardId(dartboard.id()).orElse(null);
        boolean edgeOnline = edge != null && edge.stato() == EdgeDeviceStatus.ONLINE
                && edge.ultimoHeartbeat() != null
                && !edge.ultimoHeartbeat().isBefore(Instant.now().minusSeconds(edge.sogliaOfflineS()));
        return new DartboardDTO(dartboard.id(), dartboard.localeId(), dartboard.identificativoLocale(),
                dartboard.gameTypeId(), dartboard.stato().name(), dartboard.createdAt().toString(),
                dartboard.stato() == DartboardStatus.DISPONIBILE && edgeOnline,
                edge == null ? "ASSENTE" : edgeOnline ? "ONLINE" : "OFFLINE");
    }

    /**
     * Richiede un testo presente e non composto soltanto da spazi.
     *
     * @param value testo da elaborare
     * @param field nome del campo da leggere
     * @throws ValidationException se viene rilevato l'errore «CAMPO_MANCANTE»
     *  
     */
    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("CAMPO_MANCANTE", "Campo obbligatorio mancante: " + field);
        }
    }
}
