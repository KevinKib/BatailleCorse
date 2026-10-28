package org.kevinkib.cardgames.draughts.presentation;

import java.util.List;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.IllegalMoveException;
import org.kevinkib.cardgames.draughts.presentation.api.DraughtsMovePayload;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.draughts.presentation.dto.event.DraughtsEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Draughts WebSocket endpoints, all under /draughts. They only authenticate the seat from its token and map payloads
 * onto domain arguments: every rule is in the aggregate. A rejected command answers the sender alone with an
 * ErrorResponse whose message is a locale key ({@code draughts.errors.*}) and which carries its current state.
 * Resignation is the shared /forfeit.
 */
@Controller
public class DraughtsWebSocketController {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster;
    private final SeatCommands seatCommands;
    private final DraughtsGameActions actions;

    public DraughtsWebSocketController(SessionService sessionService,
                                       SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster,
                                       SeatCommands seatCommands,
                                       DraughtsGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/draughts/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), DraughtsEventType.START, (gameId, seat) -> {
            if (!DraughtsFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a draughts game");
            }
            Draughts game = sessionService.startGame(gameId, payload.token(), Draughts.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, DraughtsEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/draughts/move")
    public void move(@Payload DraughtsMovePayload p) {
        handle(p.gameId(), p.token(), DraughtsEventType.MOVE, (id, seat) -> {
            if (p.from() == null || p.to() == null) {
                throw new IllegalMoveException();
            }
            List<Integer> captures = p.captures() == null ? List.of() : p.captures();
            actions.move(id, seat, p.from(), p.to(), captures);
        });
    }

    @MessageMapping("/draughts/offerDraw")
    public void offerDraw(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), DraughtsEventType.DRAW_OFFERED, (id, seat) -> actions.offerDraw(id, seat));
    }

    @MessageMapping("/draughts/acceptDraw")
    public void acceptDraw(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), DraughtsEventType.GAME_OVER, (id, seat) -> actions.acceptDraw(id, seat));
    }

    @MessageMapping("/draughts/declineDraw")
    public void declineDraw(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), DraughtsEventType.DRAW_DECLINED, (id, seat) -> actions.declineDraw(id, seat));
    }

    private void handle(String rawGameId, String token, DraughtsEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, Draughts.class), seat));
    }
}
