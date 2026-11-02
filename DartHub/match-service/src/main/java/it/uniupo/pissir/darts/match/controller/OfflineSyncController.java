package it.uniupo.pissir.darts.match.controller;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.json.JsonSupport;
import it.uniupo.pissir.darts.common.security.AuthenticatedUser;
import it.uniupo.pissir.darts.common.security.JwtTokenService;
import it.uniupo.pissir.darts.common.security.RequestAuthentication;
import it.uniupo.pissir.darts.match.dto.ImportOfflineRequestDTO;
import it.uniupo.pissir.darts.match.dto.OfflineBatchDTO;
import it.uniupo.pissir.darts.match.model.Role;
import it.uniupo.pissir.darts.match.service.OfflineSyncService;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.List;

/**
 * POST /partite/sync-offline, POST /partite/import-offline — EDGE_DEVICE.
 *
 * @see it.uniupo.pissir.darts.match.service.OfflineSyncService
 *  
 */
public final class OfflineSyncController {

    private final OfflineSyncService offlineSyncService;
    private final JwtTokenService jwtTokenService;

    /**
     * Inizializza {@code OfflineSyncController} con i dati e le dipendenze indicati.
     *
     * @param offlineSyncService componente {@link it.uniupo.pissir.darts.match.service.OfflineSyncService} utilizzato dall'operazione
     * @param jwtTokenService servizio di generazione e verifica dei JWT
     * @see it.uniupo.pissir.darts.match.service.OfflineSyncService
     *  
     */
    public OfflineSyncController(OfflineSyncService offlineSyncService, JwtTokenService jwtTokenService) {
        this.offlineSyncService = offlineSyncService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Registra gli endpoint HTTP del componente. 
     */
    public void register() {
        Spark.post("/partite/sync-offline", this::syncOffline);
        Spark.post("/partite/import-offline", this::importOffline);
    }

    /**
     * Riconcilia con il server i tiri conservati durante la disconnessione.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «dartboardId e matchId sono obbligatori»  
     *  
     */
    private Object syncOffline(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.EDGE_DEVICE.name());
        OfflineBatchDTO body = JsonSupport.fromJson(req.body(), OfflineBatchDTO.class);
        if (body.dartboardId() == null || body.matchId() == null) {
            throw new ValidationException("dartboardId e matchId sono obbligatori");
        }
        RequestAuthentication.requireOwnDartboard(user, body.dartboardId());
        List<it.uniupo.pissir.darts.match.dto.ThrowBatchItemDTO> tiri = body.tiri() != null ? body.tiri() : List.of();
        res.type("application/json");
        return JsonSupport.toJson(offlineSyncService.syncOffline(body.dartboardId(), body.matchId(), body.clockOffsetMs(), tiri));
    }

    /**
     * Importa sul server una partita creata localmente.
     *
     * @param req richiesta HTTP ricevuta
     * @param res risposta HTTP da compilare
     * @return il corpo della risposta HTTP
     * @throws ValidationException se viene rilevato l'errore «localUuid, dartboardId, localeId, gameTypeId, scoringRule, partecipanti e tiri sono obbligatori»  
     *  
     */
    private Object importOffline(Request req, Response res) {
        AuthenticatedUser user = authenticate(req);
        RequestAuthentication.requireAnyRole(user, Role.EDGE_DEVICE.name());
        ImportOfflineRequestDTO body = JsonSupport.fromJson(req.body(), ImportOfflineRequestDTO.class);
        if (body.localUuid() == null || body.dartboardId() == null || body.localeId() == null
                || body.gameTypeId() == null || body.scoringRule() == null
                || body.partecipanti() == null || body.partecipanti().isEmpty() || body.tiri() == null) {
            throw new ValidationException("localUuid, dartboardId, localeId, gameTypeId, scoringRule, partecipanti e tiri sono obbligatori");
        }
        RequestAuthentication.requireOwnDartboard(user, body.dartboardId());
        if (user.localeId() == null || !user.localeId().equals(body.localeId())) {
            throw new it.uniupo.pissir.darts.common.exception.ForbiddenException("LOCALE_NON_PROPRIO",
                    "Il dispositivo non è associato a questo locale");
        }
        res.status(200);
        res.type("application/json");
        return JsonSupport.toJson(offlineSyncService.importOffline(body));
    }

    /**
     * Verifica le credenziali e i dati necessari all'operazione.
     *
     * @param req richiesta HTTP ricevuta
     * @return l'oggetto {@link it.uniupo.pissir.darts.common.security.AuthenticatedUser} prodotto o recuperato
     * @see it.uniupo.pissir.darts.common.security.AuthenticatedUser
     *  
     */
    private AuthenticatedUser authenticate(Request req) {
        return RequestAuthentication.resolve(req.headers("Authorization"), jwtTokenService);
    }
}
