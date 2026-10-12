package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.presentation.dto.CardLabels;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidTokenException;
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
    private final GameMessagingService messaging;
    private final BullshitGameActions actions;

    public BullshitWebSocketController(SessionService sessionService,
                                       BullshitStateBroadcaster broadcaster,
                                       GameMessagingService messaging,
                                       BullshitGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.messaging = messaging;
        this.actions = actions;
    }

    @MessageMapping("/bullshit/start")
    public void start(@Payload GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        PlayerId actor = sessionService.findPlayerIdByToken(gameId, payload.token()).orElse(null);
        if (actor == null) {
            return;
        }
        try {
            Bullshit game = (Bullshit) sessionService.startGame(gameId, payload.token());
            sessionService.touch(gameId);
            broadcaster.broadcast(game, BullshitEventType.START.toString(), new EmptyEventData(), "Game started.");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, actor, new ErrorResponse(
                    BullshitEventType.START.toString(), e.getMessage(), null));
        }
    }

    @MessageMapping("/discard")
    public void discard(@Payload BullshitDiscardPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);

        PlayerId playerId;
        try {
            playerId = sessionService.findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }

        try {
            List<Card> cards = payload.cards().stream().map(CardLabels::card).toList();
            actions.discard(gameId, playerId, cards);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, playerId, new ErrorResponse(
                    BullshitEventType.DISCARD.toString(), e.getMessage(), broadcaster.stateFor(game, playerId)));
        }
    }

    @MessageMapping("/callBullshit")
    public void callBullshit(@Payload GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);

        PlayerId callerId;
        try {
            callerId = sessionService.findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }

        try {
            actions.callBullshit(gameId, callerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, callerId, new ErrorResponse(
                    BullshitEventType.CALL_BULLSHIT.toString(), e.getMessage(), broadcaster.stateFor(game, callerId)));
        }
    }
}
