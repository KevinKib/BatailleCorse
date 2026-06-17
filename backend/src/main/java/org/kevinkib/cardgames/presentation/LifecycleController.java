package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.PresencePayload;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/** Game-agnostic lifecycle endpoints. Pure delegation to the session/forfeit services; builds no game state. */
@Controller
public class LifecycleController {

    private final SessionService sessionService;
    private final PresenceService presenceService;

    public LifecycleController(SessionService sessionService, PresenceService presenceService) {
        this.sessionService = sessionService;
        this.presenceService = presenceService;
    }

    @MessageMapping("/presence")
    public void presence(@Payload PresencePayload payload, SimpMessageHeaderAccessor headers) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);
            presenceService.onPresence(headers.getSessionId(), gameId, playerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @MessageMapping("/forfeit")
    public void forfeit(GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);
            presenceService.forfeit(gameId, playerId, ForfeitReason.RESIGNED);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
