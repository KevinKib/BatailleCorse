package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
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
    private final DisconnectForfeitService disconnectForfeitService;

    public LifecycleController(SessionService sessionService, DisconnectForfeitService disconnectForfeitService) {
        this.sessionService = sessionService;
        this.disconnectForfeitService = disconnectForfeitService;
    }

    @MessageMapping("/presence")
    public void presence(@Payload PresencePayload payload, SimpMessageHeaderAccessor headers) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.onPresence(headers.getSessionId(), gameId, playerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @MessageMapping("/forfeit")
    public void forfeit(GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.forfeit(new Seat(gameId, playerId), ForfeitReason.RESIGNED);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
