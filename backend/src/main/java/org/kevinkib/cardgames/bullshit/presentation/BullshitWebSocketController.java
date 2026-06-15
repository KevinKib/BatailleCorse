package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.CallBullshitOutcome;
import org.kevinkib.cardgames.bullshit.domain.CannotCallBullshitException;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitCreatePayload;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitCreateEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.kevinkib.cards.domain.Card;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BullshitWebSocketController {

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 6;

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;
    private final GameMessagingService messaging;

    public BullshitWebSocketController(SessionService sessionService,
                                       BullshitStateBroadcaster broadcaster,
                                       GameMessagingService messaging) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.messaging = messaging;
    }

    @MessageMapping("/bullshit/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) BullshitCreatePayload payload) {
        int nbPlayers = (payload != null && payload.nbPlayers() != null) ? payload.nbPlayers() : MIN_PLAYERS;
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            return new ErrorResponse(LifecycleEventType.CREATE.toString(),
                    "Bullshit requires " + MIN_PLAYERS + " to " + MAX_PLAYERS + " players.", null);
        }
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;
        String name = (payload != null) ? payload.name() : null;

        Bullshit game = (Bullshit) sessionService.createGame(BullshitFactory.GAME_TYPE, nbPlayers, mode, name);

        int seatsToReturn = (mode == GameMode.SOLO) ? nbPlayers : 1;
        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < seatsToReturn; i++) {
            SessionToken token = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(i));
            tokens.put(i, token.uuid().toString());
        }

        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new BullshitCreateEventData(game.getId().uuid().toString(), BullshitFactory.GAME_TYPE, tokens),
                "Game created",
                null);
    }

    @MessageMapping("/discard")
    public void discard(@Payload BullshitDiscardPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);

        PlayerId playerId;
        try {
            playerId = sessionService.findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }

        try {
            List<Card> cards = BullshitCardMapper.toCards(payload.cards());
            ClaimTarget claimed = game.getCurrentTarget();
            game.discard(playerId, cards);
            sessionService.touch(gameId);
            broadcaster.broadcast(game,
                    BullshitEventType.DISCARD.toString(),
                    new DiscardEventData(playerId.id(), claimed.label(), cards.size()),
                    "Player " + playerId.id() + " played " + cards.size() + " card(s) as " + claimed.label() + ".");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, playerId, new ErrorResponse(
                    BullshitEventType.DISCARD.toString(), e.getMessage(), BullshitDto.forViewer(game, playerId)));
        }
    }

    @MessageMapping("/callBullshit")
    public void callBullshit(@Payload GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);

        PlayerId callerId;
        try {
            callerId = sessionService.findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }

        try {
            Discard challenged = game.getLastDiscard()
                    .orElseThrow(() -> new CannotCallBullshitException(callerId));
            List<CardDto> revealed = challenged.actualCards().stream().map(CardDto::from).toList();
            int claimantSeat = challenged.claimant().id();

            CallBullshitOutcome outcome = game.callBullshit(callerId);
            sessionService.touch(gameId);

            broadcaster.broadcast(game,
                    BullshitEventType.CALL_BULLSHIT.toString(),
                    new CallBullshitEventData(callerId.id(), claimantSeat,
                            outcome.claimWasTruthful(), outcome.pilePicker().id(), revealed),
                    "Player " + callerId.id() + " called bullshit on player " + claimantSeat + ".");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, callerId, new ErrorResponse(
                    BullshitEventType.CALL_BULLSHIT.toString(), e.getMessage(), BullshitDto.forViewer(game, callerId)));
        }
    }
}
