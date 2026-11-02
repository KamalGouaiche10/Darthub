package it.uniupo.pissir.darts.user.service;

import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.exception.UnauthorizedException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.PagedResult;
import it.uniupo.pissir.darts.user.dao.PlayerProfileDao;
import it.uniupo.pissir.darts.user.dao.UserDao;
import it.uniupo.pissir.darts.user.dto.PlayerLookupDTO;
import it.uniupo.pissir.darts.user.dto.UserDTO;
import it.uniupo.pissir.darts.user.model.ApprovalStatus;
import it.uniupo.pissir.darts.user.model.PlayerProfile;
import it.uniupo.pissir.darts.user.model.Role;
import it.uniupo.pissir.darts.user.model.User;
import it.uniupo.pissir.darts.user.mqtt.UserEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Creazione, aggiornamento, abilitazione, cambio ruolo, approvazione gestori.
 *
 * @see java.util.Set
 *  
 */
public final class UserService {

    private static final Set<Role> RUOLI_AUTOREGISTRABILI = Set.of(Role.PLAYER, Role.LOCALE_ADMIN);

    private final ConnectionProvider connectionProvider;
    private final UserDao userDao;
    private final PlayerProfileDao playerProfileDao;
    private final PasswordHasher passwordHasher;
    private final UserEventPublisher userEventPublisher;

    /**
     * Inizializza {@code UserService} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param userDao componente {@link it.uniupo.pissir.darts.user.dao.UserDao} utilizzato dall'operazione
     * @param playerProfileDao componente {@link it.uniupo.pissir.darts.user.dao.PlayerProfileDao} utilizzato dall'operazione
     * @param passwordHasher password hasher da utilizzare
     * @param userEventPublisher componente {@link it.uniupo.pissir.darts.user.mqtt.UserEventPublisher} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public UserService(ConnectionProvider connectionProvider, UserDao userDao, PlayerProfileDao playerProfileDao,
                        PasswordHasher passwordHasher, UserEventPublisher userEventPublisher) {
        this.connectionProvider = connectionProvider;
        this.userDao = userDao;
        this.playerProfileDao = playerProfileDao;
        this.passwordHasher = passwordHasher;
        this.userEventPublisher = userEventPublisher;
    }

    /**
     * Registra l'utente applicando i controlli previsti.
     *
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param password password da verificare o registrare
     * @param nickname nome pubblico del giocatore
     * @param role ruolo dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    public UserDTO register(String username, String email, String password, String nickname, Role role) {
        return register(username, email, password, nickname, role, null, null);
    }

    /**
     * Registra l'utente applicando i controlli previsti.
     *
     * @param username nome dell'account
     * @param email indirizzo email dell'account
     * @param password password da verificare o registrare
     * @param nickname nome pubblico del giocatore
     * @param role ruolo dell'utente
     * @param firstName nome anagrafico dell'utente
     * @param lastName cognome dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @throws ValidationException se viene rilevato l'errore «RUOLO_NON_AUTOREGISTRABILE»
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    public UserDTO register(String username, String email, String password, String nickname, Role role,
                            String firstName, String lastName) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(password, "password");
        Role ruoloEffettivo = role == null ? Role.PLAYER : role;
        if (!RUOLI_AUTOREGISTRABILI.contains(ruoloEffettivo)) {
            throw new ValidationException("RUOLO_NON_AUTOREGISTRABILE", "Ruolo non assegnabile in autoregistrazione: " + ruoloEffettivo);
        }
        boolean giocatore = ruoloEffettivo == Role.PLAYER;
        if (giocatore) {
            requireNonBlank(nickname, "nickname");
        }
        ApprovalStatus statoIniziale = ruoloEffettivo == Role.LOCALE_ADMIN ? ApprovalStatus.IN_ATTESA : ApprovalStatus.APPROVATO;

        PasswordHasher.Hashed hashed = passwordHasher.hash(password);
        String finalFirstName = cleanName(firstName);
        String finalLastName = cleanName(lastName);
        long userId = connectionProvider.withTransaction(connection -> {
            Instant createdAt = Instant.now();
            long id = finalFirstName == null && finalLastName == null
                    ? userDao.insert(connection, username, email, hashed.hash(), hashed.salt(), ruoloEffettivo,
                            statoIniziale, createdAt)
                    : userDao.insert(connection, username, email, hashed.hash(), hashed.salt(), ruoloEffettivo,
                            statoIniziale, createdAt, finalFirstName, finalLastName);
            if (giocatore) {
                playerProfileDao.insert(connection, id, nickname.trim(), null);
            }
            return id;
        });
        if (statoIniziale == ApprovalStatus.APPROVATO) {
            userEventPublisher.publishConteggioCambiato(userId, true);
        }
        return toDto(requireUser(userId), playerProfileDao.findById(userId).orElse(null));
    }

    /**
     * Cerca utenti tutti.
     *
     * @param page parametri di paginazione
     * @param roleFilter filtro per ruolo dell'utente
     * @param approvalStatusFilter filtro per stato di approvazione
     * @return la pagina dei risultati e i dati di paginazione
     * @see it.uniupo.pissir.darts.common.persistence.PagedResult
     *  
     */
    public PagedResult<UserDTO> findAll(Page page, Role roleFilter, ApprovalStatus approvalStatusFilter) {
        var users = userDao.search(roleFilter, approvalStatusFilter, page);
        long totale = userDao.countSearch(roleFilter, approvalStatusFilter);
        var dtos = users.stream().map(this::toDtoLoadingProfile).toList();
        return PagedResult.of(dtos, page, totale);
    }

    /**
     * Cerca l'utente usando i dati della richiesta.
     *
     * @param id identificativo dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    public UserDTO findByIdDto(long id) {
        User user = requireUser(id);
        return toDtoLoadingProfile(user);
    }

    /**
     * Ricerca il giocatore per nome dell'account.
     *
     * @param username nome dell'account
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.PlayerLookupDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.PlayerLookupDTO
     *  
     */
    public PlayerLookupDTO lookupPlayerByUsername(String username) {
        requireNonBlank(username, "username");
        String cercato = username.trim();
        return toLookupDto(userDao.findByUsername(cercato), "username '" + cercato + "'");
    }

    /**
     * Ricerca il giocatore per identificativo.
     *
     * @param id identificativo dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.PlayerLookupDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.PlayerLookupDTO
     *  
     */
    public PlayerLookupDTO lookupPlayerById(long id) {
        return toLookupDto(userDao.findById(id), "identificativo " + id);
    }

    /**
     * Converte il giocatore nei dati essenziali per la ricerca.
     *
     * @param trovato trovato da utilizzare
     * @param descrizioneRicerca descrizione ricerca da utilizzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.PlayerLookupDTO} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Nessun giocatore con»
     * @see it.uniupo.pissir.darts.user.dto.PlayerLookupDTO
     *  
     */
    private PlayerLookupDTO toLookupDto(Optional<User> trovato, String descrizioneRicerca) {
        User user = trovato
                .filter(u -> u.role() == Role.PLAYER && u.enabled())
                .orElseThrow(() -> new NotFoundException("GIOCATORE_NON_TROVATO",
                        "Nessun giocatore con " + descrizioneRicerca));
        String nickname = playerProfileDao.findById(user.id()).map(PlayerProfile::nickname).orElse(null);
        return new PlayerLookupDTO(user.id(), user.username(), nickname);
    }

    /**
     * Aggiorna l'utente dopo i controlli previsti.
     *
     * @param id identificativo dell'utente
     * @param email indirizzo email dell'account
     * @param nickname nome pubblico del giocatore
     * @param avatarUrl indirizzo dell'immagine del profilo
     * @param firstName nome anagrafico dell'utente
     * @param lastName cognome dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    public UserDTO update(long id, String email, String nickname, String avatarUrl,
                          String firstName, String lastName) {
        User current = requireUser(id);
        if (nickname != null) requireNonBlank(nickname, "nickname");
        connectionProvider.withTransaction(connection -> {
            if ((email != null && !email.isBlank()) || firstName != null || lastName != null) {
                userDao.update(connection, new User(current.id(), current.username(),
                        email != null && !email.isBlank() ? email : current.email(), current.passwordHash(),
                        current.salt(), current.role(), current.enabled(), current.approvalStatus(), current.createdAt(),
                        firstName != null ? cleanName(firstName) : current.firstName(),
                        lastName != null ? cleanName(lastName) : current.lastName()));
            }
            if (nickname != null || avatarUrl != null) {
                PlayerProfile existing = playerProfileDao.findById(id).orElse(null);
                String finalNickname = nickname != null ? nickname.trim() : (existing != null ? existing.nickname() : null);
                String finalAvatar = avatarUrl != null ? avatarUrl : (existing != null ? existing.avatarUrl() : null);
                if (existing != null) {
                    playerProfileDao.update(connection, id, finalNickname, finalAvatar);
                } else if (current.role() == Role.PLAYER && finalNickname != null) {
                    playerProfileDao.insert(connection, id, finalNickname, finalAvatar);
                }
            }
            return null;
        });
        return findByIdDto(id);
    }

    /**
     * Verifica la password attuale e registra quella nuova.
     *
     * @param id identificativo dell'utente
     * @param oldPassword password attuale da verificare
     * @param newPassword nuova password dell'account
     * @throws UnauthorizedException se viene rilevato l'errore «Vecchia password non corretta»
     *  
     */
    public void changePassword(long id, String oldPassword, String newPassword) {
        requireNonBlank(newPassword, "newPassword");
        User current = requireUser(id);
        if (!passwordHasher.matches(oldPassword == null ? "" : oldPassword, current.salt(), current.passwordHash())) {
            throw new UnauthorizedException("Vecchia password non corretta");
        }
        PasswordHasher.Hashed hashed = passwordHasher.hash(newPassword);
        User updated = new User(current.id(), current.username(), current.email(), hashed.hash(), hashed.salt(),
                current.role(), current.enabled(), current.approvalStatus(), current.createdAt(),
                current.firstName(), current.lastName());
        userDao.update(updated);
    }

    /**
     * Modifica l'abilitazione dell'account.
     *
     * @param id identificativo dell'utente
     * @param enabled indicatore di abilitazione dell'account
     *  
     */
    public void setEnabled(long id, boolean enabled) {
        User current = requireUser(id);
        User updated = new User(current.id(), current.username(), current.email(), current.passwordHash(),
                current.salt(), current.role(), enabled, current.approvalStatus(), current.createdAt(),
                current.firstName(), current.lastName());
        userDao.update(updated);
        pubblicaSeVariato(current, updated);
    }

    /**
     * Aggiorna il ruolo dell'utente dopo i controlli di autorizzazione.
     *
     * @param id identificativo dell'utente
     * @param role ruolo dell'utente
     * @see it.uniupo.pissir.darts.user.model.Role
     *  
     */
    public void changeRole(long id, Role role) {
        User current = requireUser(id);
        userDao.update(new User(current.id(), current.username(), current.email(), current.passwordHash(),
                current.salt(), role, current.enabled(), current.approvalStatus(), current.createdAt(),
                current.firstName(), current.lastName()));
    }

    /**
     * Aggiorna lo stato di approvazione dell'account.
     *
     * @param id identificativo dell'utente
     * @param approvalStatus stato di approvazione
     * @see it.uniupo.pissir.darts.user.model.ApprovalStatus
     *  
     */
    public void changeApprovalStatus(long id, ApprovalStatus approvalStatus) {
        User current = requireUser(id);
        User updated = new User(current.id(), current.username(), current.email(), current.passwordHash(),
                current.salt(), current.role(), current.enabled(), approvalStatus, current.createdAt(),
                current.firstName(), current.lastName());
        userDao.update(updated);
        pubblicaSeVariato(current, updated);
    }

    /**
     * Rimuove l'utente verificando i vincoli dell'operazione.
     *
     * @param id identificativo dell'utente
     *  
     */
    public void delete(long id) {
        User current = requireUser(id);
        if (current.contaAiFiniStatistiche()) {
            userEventPublisher.publishConteggioCambiato(id, false);
        }
        userDao.delete(id);
    }

    /**
     * Pubblica se variato tramite il collegamento MQTT.
     *
     * @param prima utente da elaborare
     * @param dopo utente da elaborare
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    private void pubblicaSeVariato(User prima, User dopo) {
        boolean contavaPrima = prima.contaAiFiniStatistiche();
        boolean contaDopo = dopo.contaAiFiniStatistiche();
        if (contavaPrima != contaDopo) {
            userEventPublisher.publishConteggioCambiato(dopo.id(), contaDopo);
        }
    }

    /**
     * Richiede che l'utente esista e sia accessibile.
     *
     * @param id identificativo dell'utente
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.model.User} prodotto o recuperato
     * @throws NotFoundException se viene rilevato l'errore «Utente non trovato»
     * @see it.uniupo.pissir.darts.user.model.User
     *  
     */
    private User requireUser(long id) {
        return userDao.findById(id).orElseThrow(() -> new NotFoundException("UTENTE_NON_TROVATO", "Utente non trovato"));
    }

    /**
     * Carica il profilo e compone i dati pubblici dell'utente.
     *
     * @param user utente da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    private UserDTO toDtoLoadingProfile(User user) {
        return toDto(user, playerProfileDao.findById(user.id()).orElse(null));
    }

    /**
     * Converte l'oggetto del dominio nel formato esposto dalle API.
     *
     * @param user utente da elaborare
     * @param profile profilo del giocatore da elaborare
     * @return l'oggetto {@link it.uniupo.pissir.darts.user.dto.UserDTO} prodotto o recuperato
     * @see it.uniupo.pissir.darts.user.dto.UserDTO
     *  
     */
    private UserDTO toDto(User user, PlayerProfile profile) {
        return new UserDTO(user.id(), user.username(), user.email(), user.role().name(), user.enabled(),
                user.approvalStatus().name(),
                profile != null ? profile.nickname() : null,
                profile != null ? profile.avatarUrl() : null,
                user.createdAt().toString(), user.firstName(), user.lastName());
    }

    /**
     * Normalizza il nome ricevuto prima di utilizzarlo.
     *
     * @param value testo da elaborare
     * @return il testo o il valore di configurazione richiesto
     *  
     */
    private static String cleanName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
