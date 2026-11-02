package it.uniupo.pissir.darts.game.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.game.dao.GameTypeDao;
import it.uniupo.pissir.darts.game.dto.GameTypeDTO;
import it.uniupo.pissir.darts.game.model.GameType;

/**
 * Tipologie di gioco.
 *
 * @see it.uniupo.pissir.darts.game.dao.GameTypeDao
 *  
 */
public final class GameTypeService {

    private static final String DEFAULT_MOTORE = "darts";

    private final GameTypeDao gameTypeDao;

    /**
     * Inizializza {@code GameTypeService} con i dati e le dipendenze indicati.
     *
     * @param gameTypeDao componente {@link it.uniupo.pissir.darts.game.dao.GameTypeDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.game.dao.GameTypeDao
     *  
     */
    public GameTypeService(GameTypeDao gameTypeDao) {
        this.gameTypeDao = gameTypeDao;
    }

    /**
     * Verifica il requisito gioco tipo prima di eseguire l'operazione.
     *
     * @param id identificativo del gioco
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.GameType} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Gioco non trovato»
     * @see it.uniupo.pissir.darts.game.model.GameType
     *  
     */
    public GameType requireGameType(long id) {
        return gameTypeDao.findById(id).orElseThrow(() -> new NotFoundException("GIOCO_NON_TROVATO", "Gioco non trovato"));
    }

    /**
     * Crea il gioco dopo la validazione dei dati.
     *
     * @param nome nome della risorsa
     * @param descrizione descrizione della risorsa
     * @param motore nome del motore delle regole
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.GameTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.GameTypeDTO
     *  
     */
    public GameTypeDTO create(String nome, String descrizione, String motore) {
        requireNonBlank(nome, "nome");
        String motoreEffettivo = motore != null && !motore.isBlank() ? motore : DEFAULT_MOTORE;
        GameType created = gameTypeDao.insert(new GameType(0, nome, descrizione, motoreEffettivo));
        return toDto(created);
    }

    /**
     * Cerca giochi tutti.
     *
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<GameTypeDTO> findAll(Page page) {
        var list = gameTypeDao.findAll(page).stream().map(this::toDto).toList();
        return PagedResult.of(list, page, gameTypeDao.count());
    }

    /**
     * Cerca il gioco usando i dati della richiesta.
     *
     * @param id identificativo del gioco
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.GameTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.GameTypeDTO
     *  
     */
    public GameTypeDTO findByIdDto(long id) {
        return toDto(requireGameType(id));
    }

    /**
     * Aggiorna il gioco dopo i controlli previsti.
     *
     * @param id identificativo del gioco
     * @param nome nome della risorsa
     * @param descrizione descrizione della risorsa
     * @param motore nome del motore delle regole
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.GameTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.GameTypeDTO
     *  
     */
    public GameTypeDTO update(long id, String nome, String descrizione, String motore) {
        GameType current = requireGameType(id);
        String newNome = nome != null && !nome.isBlank() ? nome : current.nome();
        String newDescrizione = descrizione != null ? descrizione : current.descrizione();
        String newMotore = motore != null && !motore.isBlank() ? motore : current.motore();
        gameTypeDao.update(new GameType(current.id(), newNome, newDescrizione, newMotore));
        return findByIdDto(id);
    }

    /**
     * Rimuove il gioco verificando i vincoli dell'operazione.
     *
     * @param id identificativo del gioco
     *  
     */
    public void delete(long id) {
        requireGameType(id);
        gameTypeDao.delete(id);
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param gameType gioco da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.GameTypeDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.GameTypeDTO
     *  
     */
    private GameTypeDTO toDto(GameType gameType) {
        return new GameTypeDTO(gameType.id(), gameType.nome(), gameType.descrizione(), gameType.motore());
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
