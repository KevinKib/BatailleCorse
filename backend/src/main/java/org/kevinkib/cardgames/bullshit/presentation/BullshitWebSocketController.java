package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.presentation.dto.CardLabels;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class BullshitWebSocketController {

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;
    private final SeatCommands seatCommands;
    private final BullshitGameActions actions;

    public BullshitWebSocketController(SessionService sessionService,
                                       BullshitStateBroadcaster broadcaster,
                                       SeatCommands seatCommands,
                                       BullshitGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/bullshit/start")
    public void start(@Payload GameActionPayload payload) {
        seatCommands.handle(payload.gameId(), payload.token(), BullshitEventType.START.toString(), (gameId, seat) -> {
            if (!BullshitFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Bullshit game");
            }
            Bullshit game = sessionService.startGame(gameId, payload.token(), Bullshit.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, BullshitEventType.START.toString(), new EmptyEventData(), "Game started.");
        }, this::stateFor);
    }

    @MessageMapping("/discard")
    public void discard(@Payload BullshitDiscardPayload payload) {
        seatCommands.handle(payload.gameId(), payload.token(), BullshitEventType.DISCARD.toString(), (gameId, seat) -> {
            List<Card> cards = payload.cards().stream().map(CardLabels::card).toList();
            actions.discard(gameId, seat, cards);
        }, this::stateFor);
    }

    @MessageMapping("/callBullshit")
    public void callBullshit(@Payload GameActionPayload payload) {
        seatCommands.handle(payload.gameId(), payload.token(), BullshitEventType.CALL_BULLSHIT.toString(),
                (gameId, seat) -> actions.callBullshit(gameId, seat), this::stateFor);
    }

    /** The sender's state; throws while no game runs yet, in which case the error carries none. */
    private Object stateFor(GameId gameId, PlayerId seat) {
        return broadcaster.stateFor(sessionService.getGame(gameId, Bullshit.class), seat);
    }
}
