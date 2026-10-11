package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.LobbyOptionsPayload;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Game-agnostic lobby commands. The token proves membership of the game, so no game type is checked here;
 * the rules (host only, lobby only, what a valid option is) live in the session core and the game.
 */
@Controller
public class LobbyController {

    private final SessionService sessionService;
    private final GameMessagingService messaging;

    public LobbyController(SessionService sessionService, GameMessagingService messaging) {
        this.sessionService = sessionService;
        this.messaging = messaging;
    }

    @MessageMapping("/lobby/setOptions")
    public void setOptions(@Payload LobbyOptionsPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        PlayerId actor = sessionService.findPlayerIdByToken(gameId, payload.token()).orElse(null);
        if (actor == null) {
            return;
        }
        try {
            Map<String, String> values = payload.values() == null ? Map.of() : payload.values();
            for (LobbyView view : sessionService.updateOptions(gameId, payload.token(), values)) {
                messaging.sendToSeat(gameId, new PlayerId(view.mySeat()), new SuccessResponse(
                        LifecycleEventType.JOIN.toString(), new EmptyEventData(), "Game options changed.", view));
            }
            sessionService.touch(gameId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, actor,
                    new ErrorResponse(LifecycleEventType.JOIN.toString(), e.getMessage(), null));
        }
    }
}
