package it.uniupo.pissir.darts.game.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.game.dao.ScoringRuleDao;
import it.uniupo.pissir.darts.game.dto.ScoringRuleDTO;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;

/**
 * Valida una regola di punteggio e la espone (in lettura) a match-service, che la userà con DartsRuleEngine per ogni tiro.
 *
 * @see it.uniupo.pissir.darts.game.dao.ScoringRuleDao
 *  
 */
public final class ScoringRuleService {

    private static final int DEFAULT_MAX_TIRI_PER_TURNO = 3;

    private final ScoringRuleDao scoringRuleDao;
    private final GameTypeService gameTypeService;

    /**
     * Inizializza {@code ScoringRuleService} con i dati e le dipendenze indicati.
     *
     * @param scoringRuleDao componente {@link it.uniupo.pissir.darts.game.dao.ScoringRuleDao} utilizzato dall'operazione
     * @param gameTypeService componente {@link it.uniupo.pissir.darts.game.service.GameTypeService} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.game.dao.ScoringRuleDao
     *  
     */
    public ScoringRuleService(ScoringRuleDao scoringRuleDao, GameTypeService gameTypeService) {
        this.scoringRuleDao = scoringRuleDao;
        this.gameTypeService = gameTypeService;
    }

    /**
     * Verifica il requisito scoring regola prima di eseguire l'operazione.
     *
     * @param id identificativo della regola di punteggio
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Regola non trovata»
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    public ScoringRule requireScoringRule(long id) {
        return scoringRuleDao.findById(id)
                .orElseThrow(() -> new NotFoundException("REGOLA_NON_TROVATA", "Regola non trovata"));
    }

    /**
     * Crea la regola di punteggio dopo la validazione dei dati.
     *
     * @param gameTypeId identificativo del gioco
     * @param variante nome della variante del gioco
     * @param direzione direzione di variazione del punteggio
     * @param punteggioIniziale punteggio iniziale previsto dalla regola
     * @param punteggioObiettivo punteggio da raggiungere per vincere
     * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
     * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
     * @param maxTiriPerTurno numero massimo di tiri per turno
     * @param parametriJson parametri aggiuntivi della regola in JSON
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.ScoringRuleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.ScoringRuleDTO
     *  
     */
    public ScoringRuleDTO create(long gameTypeId, String variante, String direzione, Integer punteggioIniziale,
                                  Integer punteggioObiettivo, Boolean chiusuraDouble, Boolean bustAnnullaTurno,
                                  Integer maxTiriPerTurno, String parametriJson) {
        gameTypeService.requireGameType(gameTypeId);
        requireNonBlank(variante, "variante");
        Direzione parsedDirezione = direzione != null && !direzione.isBlank() ? parseDirezione(direzione) : Direzione.DECRESCENTE;
        int iniziale = punteggioIniziale != null ? punteggioIniziale : 0;
        int obiettivo = punteggioObiettivo != null ? punteggioObiettivo : 0;
        boolean doubleOut = chiusuraDouble != null && chiusuraDouble;
        boolean bustAnnulla = bustAnnullaTurno == null || bustAnnullaTurno;
        int maxTiri = maxTiriPerTurno != null ? maxTiriPerTurno : DEFAULT_MAX_TIRI_PER_TURNO;
        String parametri = parametriJson != null && !parametriJson.isBlank() ? parametriJson : "{}";

        validaCoerenza(parsedDirezione, iniziale, obiettivo, maxTiri);

        ScoringRule created = scoringRuleDao.insert(new ScoringRule(0, gameTypeId, variante, parsedDirezione,
                iniziale, obiettivo, doubleOut, bustAnnulla, maxTiri, parametri));
        return toDto(created);
    }

    /**
     * Cerca regole di punteggio per gioco tipo.
     *
     * @param gameTypeId identificativo del gioco
     * @param page parametri di paginazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<ScoringRuleDTO> findByGameType(long gameTypeId, Page page) {
        gameTypeService.requireGameType(gameTypeId);
        var list = scoringRuleDao.findByGameType(gameTypeId, page).stream().map(this::toDto).toList();
        return PagedResult.of(list, page, scoringRuleDao.countByGameType(gameTypeId));
    }

    /**
     * Cerca la regola di punteggio usando i dati della richiesta.
     *
     * @param id identificativo della regola di punteggio
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.ScoringRuleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.ScoringRuleDTO
     *  
     */
    public ScoringRuleDTO findByIdDto(long id) {
        return toDto(requireScoringRule(id));
    }

    /**
     * Aggiorna la regola di punteggio dopo i controlli previsti.
     *
     * @param id identificativo della regola di punteggio
     * @param variante nome della variante del gioco
     * @param direzione direzione di variazione del punteggio
     * @param punteggioIniziale punteggio iniziale previsto dalla regola
     * @param punteggioObiettivo punteggio da raggiungere per vincere
     * @param chiusuraDouble indicatore dell'obbligo di chiusura con un doppio
     * @param bustAnnullaTurno indicatore di annullamento del turno in caso di bust
     * @param maxTiriPerTurno numero massimo di tiri per turno
     * @param parametriJson parametri aggiuntivi della regola in JSON
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.ScoringRuleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.ScoringRuleDTO
     *  
     */
    public ScoringRuleDTO update(long id, String variante, String direzione, Integer punteggioIniziale,
                                  Integer punteggioObiettivo, Boolean chiusuraDouble, Boolean bustAnnullaTurno,
                                  Integer maxTiriPerTurno, String parametriJson) {
        ScoringRule current = requireScoringRule(id);
        String newVariante = variante != null && !variante.isBlank() ? variante : current.variante();
        Direzione newDirezione = direzione != null && !direzione.isBlank() ? parseDirezione(direzione) : current.direzione();
        int newIniziale = punteggioIniziale != null ? punteggioIniziale : current.punteggioIniziale();
        int newObiettivo = punteggioObiettivo != null ? punteggioObiettivo : current.punteggioObiettivo();
        boolean newDoubleOut = chiusuraDouble != null ? chiusuraDouble : current.chiusuraDouble();
        boolean newBustAnnulla = bustAnnullaTurno != null ? bustAnnullaTurno : current.bustAnnullaTurno();
        int newMaxTiri = maxTiriPerTurno != null ? maxTiriPerTurno : current.maxTiriPerTurno();
        String newParametri = parametriJson != null && !parametriJson.isBlank() ? parametriJson : current.parametriJson();

        validaCoerenza(newDirezione, newIniziale, newObiettivo, newMaxTiri);

        scoringRuleDao.update(new ScoringRule(current.id(), current.gameTypeId(), newVariante, newDirezione,
                newIniziale, newObiettivo, newDoubleOut, newBustAnnulla, newMaxTiri, newParametri));
        return findByIdDto(id);
    }

    /**
     * Rimuove la regola di punteggio verificando i vincoli dell'operazione.
     *
     * @param id identificativo della regola di punteggio
     *  
     */
    public void delete(long id) {
        requireScoringRule(id);
        scoringRuleDao.delete(id);
    }

    /**
     * Valida coerenza per la regola di punteggio.
     *
     * @param direzione direzione di variazione del punteggio
     * @param iniziale iniziale da utilizzare
     * @param obiettivo obiettivo da utilizzare
     * @param maxTiri numero massimo di tiri
     * @throws ValidationException se viene rilevato l'errore «maxTiriPerTurno deve essere positivo»
     * @see it.uniupo.pissir.darts.game.model.Direzione
     *  
     */
    private void validaCoerenza(Direzione direzione, int iniziale, int obiettivo, int maxTiri) {
        if (maxTiri <= 0) {
            throw new ValidationException("MAX_TIRI_NON_VALIDO", "maxTiriPerTurno deve essere positivo");
        }
        if (direzione == Direzione.DECRESCENTE && iniziale < obiettivo) {
            throw new ValidationException("PUNTEGGI_INCOERENTI",
                    "Con direzione DECRESCENTE il punteggio iniziale deve essere >= all'obiettivo");
        }
        if (direzione == Direzione.CRESCENTE && iniziale > obiettivo) {
            throw new ValidationException("PUNTEGGI_INCOERENTI",
                    "Con direzione CRESCENTE il punteggio iniziale deve essere <= all'obiettivo");
        }
    }

    /**
     * Legge e converte direzione dalla richiesta o dalla configurazione.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.Direzione} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.Direzione
     *  
     */
    private Direzione parseDirezione(String value) {
        try {
            return Direzione.valueOf(value);
        } catch (RuntimeException e) {
            throw new ValidationException("DIREZIONE_NON_VALIDA", "Direzione non valida: " + value);
        }
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param rule regola di gioco da applicare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.dto.ScoringRuleDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.dto.ScoringRuleDTO
     *  
     */
    private ScoringRuleDTO toDto(ScoringRule rule) {
        return new ScoringRuleDTO(rule.id(), rule.gameTypeId(), rule.variante(), rule.direzione().name(),
                rule.punteggioIniziale(), rule.punteggioObiettivo(), rule.chiusuraDouble(), rule.bustAnnullaTurno(),
                rule.maxTiriPerTurno(), rule.parametriJson());
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
