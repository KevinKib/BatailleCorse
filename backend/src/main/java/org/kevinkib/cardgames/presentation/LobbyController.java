package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.LobbyOptionsPayload;
import org.kevinkib.cardgames.presentation.api.LobbyRenamePayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatOptionPayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatPayload;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
        Map<String, String> values = payload.values() == null ? Map.of() : payload.values();
        handle(payload.gameId(), payload.token(), "Game options changed.",
                gameId -> sessionService.updateOptions(gameId, payload.token(), values));
    }

    @MessageMapping("/lobby/addBot")
    public void addBot(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), "Bot joined.", gameId -> {
            sessionService.addBot(gameId, payload.token());
            return sessionService.lobbyViews(gameId);
        });
    }

    @MessageMapping("/lobby/removeBot")
    public void removeBot(@Payload LobbySeatPayload payload) {
        handle(payload.gameId(), payload.token(), "Bot removed from seat " + payload.seat() + ".", gameId -> {
            sessionService.removeBot(gameId, payload.token(), payload.seat());
            return sessionService.lobbyViews(gameId);
        });
    }

    @MessageMapping("/lobby/rename")
    public void rename(@Payload LobbyRenamePayload payload) {
        handle(payload.gameId(), payload.token(), "Player renamed.",
                gameId -> sessionService.renameSeat(gameId, payload.token(), payload.name()));
    }

    @MessageMapping("/lobby/setSeatOption")
    public void setSeatOption(@Payload LobbySeatOptionPayload payload) {
        handle(payload.gameId(), payload.token(), "Seat option changed.",
                gameId -> sessionService.setSeatOption(gameId, payload.token(), payload.seat(), payload.key(),
                        payload.value()));
    }

    /**
     * Runs a lobby command and sends the resulting per-viewer lobby to each human seat; a refusal goes to the
     * acting seat only, and an unknown token is ignored.
     */
    private void handle(String rawGameId, String token, String message, Function<GameId, List<LobbyView>> command) {
        GameId gameId = new GameId(rawGameId);
        PlayerId actor = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        if (actor == null) {
            return;
        }
        try {
            List<LobbyView> views = command.apply(gameId);
            sessionService.touch(gameId);
            for (LobbyView view : views) {
                messaging.sendToSeat(gameId, new PlayerId(view.mySeat()), new SuccessResponse(
                        LifecycleEventType.JOIN.toString(), new EmptyEventData(), message, view));
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, actor,
                    new ErrorResponse(LifecycleEventType.JOIN.toString(), e.getMessage(), null));
        }
    }
}
