package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.presentation.api.NinetyNinePlayPayload;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventType;
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
 * 99 WebSocket endpoints, all under /ninety-nine. They only authenticate the seat from its token and map payloads
 * onto domain arguments: every rule is in the {@link NinetyNine} aggregate. A rejected command answers the sender
 * alone with an ErrorResponse carrying its current state.
 */
@Controller
public class NinetyNineWebSocketController {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster;
    private final SeatCommands seatCommands;
    private final NinetyNineGameActions actions;

    public NinetyNineWebSocketController(SessionService sessionService,
                                         SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster,
                                         SeatCommands seatCommands,
                                         NinetyNineGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/ninety-nine/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), NinetyNineEventType.START, (gameId, seat) -> {
            if (!NinetyNineFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a 99 game");
            }
            NinetyNine game = sessionService.startGame(gameId, payload.token(), NinetyNine.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, NinetyNineEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/ninety-nine/play")
    public void play(@Payload NinetyNinePlayPayload p) {
        handle(p.gameId(), p.token(), NinetyNineEventType.PLAY, (id, seat) -> {
            if (p.card() == null) {
                throw new IllegalArgumentException("Missing field: card");
            }
            actions.play(id, seat, CardLabels.card(p.card()), p.aceValue());
        });
    }

    private void handle(String rawGameId, String token, NinetyNineEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, NinetyNine.class), seat));
    }
}
