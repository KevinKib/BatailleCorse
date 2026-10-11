package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisMovePayload;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisPlacePayload;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Three Men's Morris WebSocket endpoints, all under /three-men-morris. They only authenticate the
 * seat from its token and map payloads onto domain arguments: every rule is in the aggregate. A
 * rejected command answers the sender alone with an ErrorResponse carrying its current state.
 */
@Controller
public class ThreeMenMorrisWebSocketController {

    @FunctionalInterface
    private interface Action {
        void run(GameId gameId, PlayerId seat) throws Exception;
    }

    private final SessionService sessionService;
    private final ThreeMenMorrisStateBroadcaster broadcaster;
    private final GameMessagingService messaging;
    private final ThreeMenMorrisGameActions actions;

    public ThreeMenMorrisWebSocketController(SessionService sessionService,
                                             ThreeMenMorrisStateBroadcaster broadcaster,
                                             GameMessagingService messaging,
                                             ThreeMenMorrisGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.messaging = messaging;
        this.actions = actions;
    }

    @MessageMapping("/three-men-morris/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), ThreeMenMorrisEventType.START, (gameId, seat) -> {
            if (!ThreeMenMorrisFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Three Men's Morris game");
            }
            ThreeMenMorris game = (ThreeMenMorris) sessionService.startGame(gameId, payload.token());
            sessionService.touch(gameId);
            broadcaster.broadcast(game, ThreeMenMorrisEventType.START.toString(), new EmptyEventData(),
                    "Game started.");
        });
    }

    @MessageMapping("/three-men-morris/place")
    public void place(@Payload ThreeMenMorrisPlacePayload p) {
        handle(p.gameId(), p.token(), ThreeMenMorrisEventType.PLACE,
                (id, seat) -> actions.place(id, seat, required(p.point(), "point")));
    }

    @MessageMapping("/three-men-morris/move")
    public void move(@Payload ThreeMenMorrisMovePayload p) {
        handle(p.gameId(), p.token(), ThreeMenMorrisEventType.MOVE,
                (id, seat) -> actions.move(id, seat, required(p.from(), "from"), required(p.to(), "to")));
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value;
    }

    private void handle(String rawGameId, String token, ThreeMenMorrisEventType type, Action action) {
        GameId gameId;
        PlayerId seat;
        try {
            gameId = new GameId(rawGameId);
            seat = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
        if (seat == null) {
            return;
        }
        try {
            action.run(gameId, seat);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            Object state = null;
            try {
                state = broadcaster.stateFor(sessionService.getGame(gameId, ThreeMenMorris.class), seat);
            } catch (Exception ignored) {
                // no running game yet: the error carries no state
            }
            messaging.sendToSeat(gameId, seat, new ErrorResponse(type.toString(), e.getMessage(), state));
        }
    }
}
