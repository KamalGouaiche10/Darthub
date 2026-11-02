package it.uniupo.pissir.darts.game.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.game.dao.SensorTypeDao;
import it.uniupo.pissir.darts.game.dto.SensorTypeDTO;
import it.uniupo.pissir.darts.game.model.SensorModalita;
import it.uniupo.pissir.darts.game.model.SensorType;

/**
 * Tipi di sensore associati a un gioco.
 *
 * @see it.uniupo.pissir.darts.game.dao.SensorTypeDao
 *  
 */
public final class SensorTypeService {

    private final SensorTypeDao sensorTypeDao;
    private final GameTypeService gameTypeService;

    /**
     * Inizializza {@code SensorTypeService} con i dati e le dipendenze indicati.
     *
     * @param sensorTypeDao componente {@link it.uniupo.pissir.darts.game.dao.SensorTypeDao} utilizzato dall'operazione
     * @param gameTypeService componente {@link it.uniupo.pissir.darts.game.service.GameTypeService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.game.dao.SensorTypeDao
     *  
     */
    public SensorTypeService(SensorTypeDao sensorTypeDao, GameTypeService gameTypeService) {
        this.sensorTypeDao = sensorTypeDao;
        this.gameTypeService = gameTypeService;
    }

    /**
     * Verifica il requisito sensore tipo prima di eseguire l'operazione.
     *
     * @param id identificativo del tipo di sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.SensorType} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Sensore non trovato»
     * @see it.uniupo.pissir.darts.game.model.SensorType
     *  
     */
    public SensorType requireSensorType(long id) {
        return sensorTypeDao.findById(id)
                .orElseThrow(() -> new NotFoundException("SENSORE_NON_TROVATO", "Sensore non trovato"));
    }

    /**
     * Crea il tipo di sensore dopo la validazione dei dati.
     *
     * @param gameTypeId identificativo del gioco
     * @param nome nome della risorsa
     * @param formatoDato formato del dato prodotto dal sensore
     * @param modalita modalità della partita o del sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.SensorTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.SensorTypeDTO
     *  
     */
    public SensorTypeDTO create(long gameTypeId, String nome, String formatoDato, String modalita) {
        gameTypeService.requireGameType(gameTypeId);
        requireNonBlank(nome, "nome");
        requireNonBlank(formatoDato, "formatoDato");
        SensorModalita parsedModalita = parseModalita(modalita);
        SensorType created = sensorTypeDao.insert(new SensorType(0, gameTypeId, nome, formatoDato, parsedModalita));
        return toDto(created);
    }

    /**
     * Cerca tipi di sensore per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<SensorTypeDTO> findByGameType(long gameTypeId, Page page) {
        gameTypeService.requireGameType(gameTypeId);
        var list = sensorTypeDao.findByGameType(gameTypeId, page).stream().map(this::toDto).toList();
        return PagedResult.of(list, page, sensorTypeDao.countByGameType(gameTypeId));
    }

    /**
     * Cerca il tipo di sensore usando i dati della richiesta.
     *
     * @param id identificativo del tipo di sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.SensorTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.SensorTypeDTO
     *  
     */
    public SensorTypeDTO findByIdDto(long id) {
        return toDto(requireSensorType(id));
    }

    /**
     * Aggiorna il tipo di sensore dopo i controlli previsti.
     *
     * @param id identificativo del tipo di sensore
     * @param nome nome della risorsa
     * @param formatoDato formato del dato prodotto dal sensore
     * @param modalita modalità della partita o del sensore
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.SensorTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.SensorTypeDTO
     *  
     */
    public SensorTypeDTO update(long id, String nome, String formatoDato, String modalita) {
        SensorType current = requireSensorType(id);
        String newNome = nome != null && !nome.isBlank() ? nome : current.nome();
        String newFormato = formatoDato != null && !formatoDato.isBlank() ? formatoDato : current.formatoDato();
        SensorModalita newModalita = modalita != null && !modalita.isBlank() ? parseModalita(modalita) : current.modalita();
        sensorTypeDao.update(new SensorType(current.id(), current.gameTypeId(), newNome, newFormato, newModalita));
        return findByIdDto(id);
    }

    /**
     * Rimuove il tipo di sensore verificando i vincoli dell'operazione.
     *
     * @param id identificativo del tipo di sensore
     *  
     */
    public void delete(long id) {
        requireSensorType(id);
        sensorTypeDao.delete(id);
    }

    /**
     * Legge e converte modalita dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.SensorModalita} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.SensorModalita
     *  
     */
    private SensorModalita parseModalita(String value) {
        try {
            return SensorModalita.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("MODALITA_NON_VALIDA", "Modalità non valida: " + value);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param sensorType tipo di sensore da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.SensorTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.SensorTypeDTO
     *  
     */
    private SensorTypeDTO toDto(SensorType sensorType) {
        return new SensorTypeDTO(sensorType.id(), sensorType.gameTypeId(), sensorType.nome(),
                sensorType.formatoDato(), sensorType.modalita().name());
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
