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
import org.kevinkib.cardgames.sessionmanagement.core.application.SeatRemoval;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Game-agnostic lobby commands. The token proves membership of the game, so no game type is checked here;
 * the rules (host only, lobby only, what a valid option is) live in the session core and the game.
 */
@Controller
public class LobbyController {

    private static final Logger log = LoggerFactory.getLogger(LobbyController.class);

    private final SessionService sessionService;
    private final GameMessagingService messaging;
    private final LobbyBroadcaster lobbyBroadcaster;
    private final PresenceService presenceService;

    public LobbyController(SessionService sessionService, GameMessagingService messaging, PresenceService presenceService) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.lobbyBroadcaster = new LobbyBroadcaster(messaging);
        this.presenceService = presenceService;
    }

    @MessageMapping("/lobby/setOptions")
    public void setOptions(@Payload LobbyOptionsPayload payload) {
        Map<String, String> values = payload.values() == null ? Map.of() : payload.values();
        handle(payload.gameId(), payload.token(), "Game options changed.",
                gameId -> sessionService.updateOptions(gameId, payload.token(), values));
    }

    @MessageMapping("/lobby/addBot")
    public void addBot(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), "Bot joined.",
                gameId -> sessionService.addBotViews(gameId, payload.token()));
    }

    @MessageMapping("/lobby/removeBot")
    public void removeBot(@Payload LobbySeatPayload payload) {
        handle(payload.gameId(), payload.token(), "Bot removed from seat " + payload.seat() + ".", gameId -> sessionService.removeBotViews(gameId, payload.token(), payload.seat()));
    }

    /**
     * The host removes another human from the lobby, connected or not. The remaining humans receive their new view;
     * the removed one, if still listening on its (now unresolvable) channel, is told it was removed. A refusal
     * (not the host, host seat, bot or free seat, game started) goes to the sender alone.
     */
    @MessageMapping("/lobby/removeSeat")
    public void removeSeat(@Payload LobbySeatPayload payload) {
        AtomicReference<String> removedToken = new AtomicReference<>();
        handle(payload.gameId(), payload.token(), "A player was removed from seat " + payload.seat() + ".", gameId -> {
            SeatRemoval removal = sessionService.removeSeat(gameId, payload.token(), payload.seat());
            removedToken.set(removal.removedToken());
            return removal.views();
        });
        if (removedToken.get() != null) {
            messaging.sendToToken(new GameId(payload.gameId()), removedToken.get(),
                    new SuccessResponse(LifecycleEventType.REMOVED.toString(), new EmptyEventData(),
                            "You were removed from the lobby by the host", null));
        }
    }

    /**
     * The sender leaves the lobby; the remaining humans receive their new view. Nothing goes to the leaver (their
     * token no longer resolves) and nothing at all when the lobby disappears because no human is left.
     * When the game has started in the meantime the leaver has already torn its client down and nobody could
     * tell it so: the seat resigns through the presence path (the others see the forfeit) instead of staying
     * seated for the table to wait on.
     */
    @MessageMapping("/lobby/leave")
    public void leave(@Payload GameActionPayload payload) {
        if (resignIfStarted(payload)) {
            return;
        }
        handle(payload.gameId(), payload.token(), "A player left.",
                gameId -> sessionService.leaveLobby(gameId, payload.token()).views());
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
    private void handle(String rawGameId, String token, String message, Function<GameId, List<AddressedLobbyView>> command) {
        GameId gameId;
        try {
            gameId = new GameId(rawGameId);
            if (sessionService.findPlayerIdByToken(gameId, token).isEmpty()) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // the lobby no longer exists (a late command after the last human left): nobody to answer
            return;
        }
        try {
            List<AddressedLobbyView> views = command.apply(gameId);
            sessionService.touch(gameId);
            lobbyBroadcaster.send(gameId, views, LifecycleEventType.JOIN.toString(), new EmptyEventData(), message);
        } catch (Exception e) {
            log.debug("Lobby command refused: {}", e.getMessage());
            messaging.sendToToken(gameId, token,
                    new ErrorResponse(LifecycleEventType.JOIN.toString(), e.getMessage(), null));
        }
    }

    private boolean resignIfStarted(GameActionPayload payload) {
        try {
            GameId gameId = new GameId(payload.gameId());
            if (sessionService.findGame(gameId).isEmpty()) {
                return false;
            }
            sessionService.findPlayerIdByToken(gameId, payload.token())
                    .ifPresent(seat -> presenceService.forfeit(gameId, seat, ForfeitReason.RESIGNED));
            return true;
        } catch (IllegalArgumentException e) {
            // unknown lobby: let the regular path ignore it
            return false;
        }
    }
}
