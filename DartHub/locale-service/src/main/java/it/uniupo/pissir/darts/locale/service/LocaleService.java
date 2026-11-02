package it.uniupo.pissir.darts.locale.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.dto.LocaleDTO;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher;

import java.time.Instant;

/**
 * Creazione, aggiornamento, cancellazione, approvazione dei locali.
 *
 * @see it.uniupo.pissir.darts.locale.dao.LocaleDao
 *  
 */
public final class LocaleService {

    private final LocaleDao localeDao;
    private final LocaleEventPublisher localeEventPublisher;

    /**
     * Inizializza {@code LocaleService} con i dati e le dipendenze indicati.
     *
     * @param localeDao componente {@link it.uniupo.pissir.darts.locale.dao.LocaleDao} utilizzato dall'operazione
     * @param localeEventPublisher componente {@link it.uniupo.pissir.darts.locale.mqtt.LocaleEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.locale.dao.LocaleDao
     *  
     */
    public LocaleService(LocaleDao localeDao, LocaleEventPublisher localeEventPublisher) {
        this.localeDao = localeDao;
        this.localeEventPublisher = localeEventPublisher;
    }

    /**
     * Richiede che il locale esista e sia accessibile.
     *
     * @param id identificativo del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Locale non trovato»
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    public Locale requireLocale(long id) {
        return localeDao.findById(id).orElseThrow(() -> new NotFoundException("LOCALE_NON_TROVATO", "Locale non trovato"));
    }

    /**
     * Crea il locale dopo la validazione dei dati.
     *
     * @param nome nome della risorsa
     * @param indirizzo indirizzo del locale
     * @param tipo tipo della risorsa o dell'evento
     * @param ownerUserId identificativo del proprietario del locale
     * @param statoIniziale stato iniziale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.LocaleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.LocaleDTO
     *  
     */
    public LocaleDTO create(String nome, String indirizzo, String tipo, long ownerUserId, LocaleStatus statoIniziale) {
        requireNonBlank(nome, "nome");
        LocaleType parsedTipo = parseType(tipo);
        Locale created = localeDao.insert(new Locale(0, nome, indirizzo, parsedTipo, ownerUserId, statoIniziale, Instant.now()));
        if (created.contaAiFiniStatistiche()) {
            localeEventPublisher.publishConteggioCambiato(created.id(), true);
        }
        return toDto(created);
    }

    /**
     * Ricerca le risorse usando i filtri e la paginazione indicati.
     *
     * @param page parametri di paginazione
     * @param ownerUserIdFilter filtro per identificativo del proprietario del locale
     * @param statoFilter filtro per stato da impostare o verificare
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<LocaleDTO> search(Page page, Long ownerUserIdFilter, LocaleStatus statoFilter) {
        var list = localeDao.search(ownerUserIdFilter, statoFilter, page).stream().map(this::toDto).toList();
        return PagedResult.of(list, page, localeDao.countSearch(ownerUserIdFilter, statoFilter));
    }

    /**
     * Cerca il locale usando i dati della richiesta.
     *
     * @param id identificativo del locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.LocaleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.LocaleDTO
     *  
     */
    public LocaleDTO findByIdDto(long id) {
        return toDto(requireLocale(id));
    }

    /**
     * Aggiorna il locale dopo i controlli previsti.
     *
     * @param id identificativo del locale
     * @param nome nome della risorsa
     * @param indirizzo indirizzo del locale
     * @param tipo tipo della risorsa o dell'evento
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.LocaleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.LocaleDTO
     *  
     */
    public LocaleDTO update(long id, String nome, String indirizzo, String tipo) {
        Locale current = requireLocale(id);
        String newNome = nome != null && !nome.isBlank() ? nome : current.nome();
        String newIndirizzo = indirizzo != null ? indirizzo : current.indirizzo();
        LocaleType newTipo = tipo != null && !tipo.isBlank() ? parseType(tipo) : current.tipo();
        localeDao.update(new Locale(current.id(), newNome, newIndirizzo, newTipo, current.ownerUserId(),
                current.stato(), current.createdAt()));
        return findByIdDto(id);
    }

    /**
     * Aggiorna lo stato della risorsa dopo i controlli previsti.
     *
     * @param id identificativo del locale
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.LocaleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.LocaleDTO
     *  
     */
    public LocaleDTO changeStato(long id, LocaleStatus stato) {
        Locale current = requireLocale(id);
        Locale updated = new Locale(current.id(), current.nome(), current.indirizzo(), current.tipo(),
                current.ownerUserId(), stato, current.createdAt());
        localeDao.update(updated);
        boolean contavaPrima = current.contaAiFiniStatistiche();
        boolean contaDopo = updated.contaAiFiniStatistiche();
        if (contavaPrima != contaDopo) {
            localeEventPublisher.publishConteggioCambiato(id, contaDopo);
        }
        return findByIdDto(id);
    }

    /**
     * Rimuove il locale verificando i vincoli dell'operazione.
     *
     * @param id identificativo del locale
     *  
     */
    public void delete(long id) {
        Locale current = requireLocale(id);
        if (current.contaAiFiniStatistiche()) {
            localeEventPublisher.publishConteggioCambiato(id, false);
        }
        localeDao.delete(id);
    }

    /**
     * Legge e converte tipo dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.LocaleType} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.LocaleType
     *  
     */
    private LocaleType parseType(String value) {
        try {
            return LocaleType.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("TIPO_NON_VALIDO", "Tipo di locale non valido: " + value);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param locale locale a cui si riferiscono i dati
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.dto.LocaleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.dto.LocaleDTO
     *  
     */
    private LocaleDTO toDto(Locale locale) {
        return new LocaleDTO(locale.id(), locale.nome(), locale.indirizzo(), locale.tipo().name(),
                locale.ownerUserId(), locale.stato().name(), locale.createdAt().toString());
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
