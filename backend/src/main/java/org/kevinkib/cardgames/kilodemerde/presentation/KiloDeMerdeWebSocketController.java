package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.presentation.api.KiloDeMerdePassPayload;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.event.KiloDeMerdeEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.CardLabels;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Kilo de merde WebSocket endpoints, all under /kilo-de-merde. They only authenticate the seat from its token and map
 * payloads onto domain arguments: every rule is in the {@link KiloDeMerde} aggregate. A rejected command answers the
 * sender alone with an ErrorResponse carrying its current state.
 */
@Controller
public class KiloDeMerdeWebSocketController {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster;
    private final SeatCommands seatCommands;
    private final KiloDeMerdeGameActions actions;

    public KiloDeMerdeWebSocketController(SessionService sessionService,
                                          SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster,
                                          SeatCommands seatCommands,
                                          KiloDeMerdeGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/kilo-de-merde/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), KiloDeMerdeEventType.START, (gameId, seat) -> {
            if (!KiloDeMerdeFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Kilo de merde game");
            }
            KiloDeMerde game = sessionService.startGame(gameId, payload.token(), KiloDeMerde.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, KiloDeMerdeEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/kilo-de-merde/pass")
    public void pass(@Payload KiloDeMerdePassPayload p) {
        handle(p.gameId(), p.token(), KiloDeMerdeEventType.PASS_CHOSEN, (id, seat) -> {
            if (p.card() == null) {
                throw new IllegalArgumentException("Missing field: card");
            }
            actions.choosePass(id, seat, CardLabels.card(p.card()));
        });
    }

    @MessageMapping("/kilo-de-merde/slap")
    public void slap(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), KiloDeMerdeEventType.SLAP,
                (id, seat) -> actions.slap(id, seat));
    }

    private void handle(String rawGameId, String token, KiloDeMerdeEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, KiloDeMerde.class), seat));
    }
}
