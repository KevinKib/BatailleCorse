package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.BatailleCorseEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Bataille Corse WebSocket endpoints. They only map payloads onto use cases: the seat is authenticated from its
 * token by {@link SeatCommands}, and every rule is in the domain. A refused command answers the sender alone
 * with an error carrying its current state.
 */
@Controller
public class BatailleCorseWebSocketController {

    private final SessionService sessionService;
    private final SeatCommands seatCommands;
    private final BatailleCorseGameActions actions;
    private final BatailleCorseStateBroadcaster broadcaster;

    public BatailleCorseWebSocketController(SessionService sessionService,
                                            SeatCommands seatCommands,
                                            BatailleCorseGameActions actions,
                                            BatailleCorseStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.seatCommands = seatCommands;
        this.actions = actions;
        this.broadcaster = broadcaster;
    }

    /** Room model: the host starts the game once enough seats are taken (humans or bots). */
    @MessageMapping("/bataillecorse/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload, BatailleCorseEventType.START, (gameId, seat) -> {
            if (!BatailleCorseFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Bataille Corse game");
            }
            BatailleCorse game = sessionService.startGame(gameId, payload.token(), BatailleCorse.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, BatailleCorseEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/send")
    public void send(GameActionPayload payload) {
        handle(payload, BatailleCorseEventType.SEND, actions::send);
    }

    @MessageMapping("/slap")
    public void slap(GameActionPayload payload) {
        handle(payload, BatailleCorseEventType.SLAP, actions::slap);
    }

    @MessageMapping("/grab")
    public void grab(GameActionPayload payload) {
        handle(payload, BatailleCorseEventType.GRAB, actions::grab);
    }

    /** The state of a running game for an error reply; absent (null) while the room is still a lobby. */
    private void handle(GameActionPayload payload, BatailleCorseEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(payload.gameId(), payload.token(), type.toString(), action,
                (gameId, seat) -> sessionService.findGame(gameId)
                        .filter(BatailleCorse.class::isInstance)
                        .map(game -> BatailleCorseDto.from((BatailleCorse) game))
                        .orElse(null));
    }
}
