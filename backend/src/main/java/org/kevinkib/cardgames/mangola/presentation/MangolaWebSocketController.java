package org.kevinkib.cardgames.mangola.presentation;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.presentation.api.MangolaArrangePayload;
import org.kevinkib.cardgames.mangola.presentation.api.MangolaSowPayload;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.mangola.presentation.dto.event.MangolaEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Mangola WebSocket endpoints, all under /mangola. They only authenticate the seat from its token and map
 * payloads onto domain arguments: every rule is in the aggregate. A rejected command answers the sender alone
 * with an ErrorResponse carrying its current state (its own projection, so never the other seat's draft).
 */
@Controller
public class MangolaWebSocketController {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Mangola, MangolaDto> broadcaster;
    private final SeatCommands seatCommands;
    private final MangolaGameActions actions;

    public MangolaWebSocketController(SessionService sessionService,
                                      SeatStateBroadcaster<Mangola, MangolaDto> broadcaster,
                                      SeatCommands seatCommands,
                                      MangolaGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/mangola/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), MangolaEventType.START, (gameId, seat) -> {
            if (!MangolaFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Mangola game");
            }
            Mangola game = sessionService.startGame(gameId, payload.token(), Mangola.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, MangolaEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/mangola/arrange")
    public void arrange(@Payload MangolaArrangePayload p) {
        handle(p.gameId(), p.token(), MangolaEventType.LAYOUT_CHANGED,
                (id, seat) -> actions.arrange(id, seat, countsOf(p.counts())));
    }

    @MessageMapping("/mangola/confirmLayout")
    public void confirmLayout(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), MangolaEventType.LAYOUT_CONFIRMED, (id, seat) -> actions.confirmLayout(id, seat));
    }

    @MessageMapping("/mangola/editLayout")
    public void editLayout(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), MangolaEventType.LAYOUT_WITHDRAWN, (id, seat) -> actions.editLayout(id, seat));
    }

    @MessageMapping("/mangola/sow")
    public void sow(@Payload MangolaSowPayload p) {
        handle(p.gameId(), p.token(), MangolaEventType.SOWN,
                (id, seat) -> actions.sow(id, seat, required(p.pit(), "pit")));
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value;
    }

    /** The counts as an int array; the domain checks their number, their sign and their sum. */
    private static int[] countsOf(List<Integer> counts) {
        if (counts == null || counts.stream().anyMatch(c -> c == null)) {
            throw new IllegalArgumentException("Missing field: counts");
        }
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }

    private void handle(String rawGameId, String token, MangolaEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, Mangola.class), seat));
    }
}
