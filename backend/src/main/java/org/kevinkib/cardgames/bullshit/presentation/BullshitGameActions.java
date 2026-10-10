package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.CallBullshitOutcome;
import org.kevinkib.cardgames.bullshit.domain.CannotCallBullshitException;
import org.kevinkib.cardgames.bullshit.domain.CardsNotInHandException;
import org.kevinkib.cardgames.bullshit.domain.FinishedGameException;
import org.kevinkib.cardgames.bullshit.domain.InvalidDiscardCountException;
import org.kevinkib.cardgames.bullshit.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The discard and call-Bullshit use cases, shared by the WebSocket controller (a human acting
 * through a token) and the bot coordinator (a bot acting for its seat): domain call, session touch,
 * then one broadcast. Authentication stays with the caller; domain exceptions propagate to it.
 */
public class BullshitGameActions {

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;

    public BullshitGameActions(SessionService sessionService, BullshitStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void discard(GameId gameId, PlayerId playerId, List<Card> cards)
            throws FinishedGameException, NotPlayersTurnException, InvalidDiscardCountException, CardsNotInHandException {
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);
        ClaimTarget claimed = game.getCurrentTarget();
        game.discard(playerId, cards);
        sessionService.touch(gameId);
        broadcaster.broadcast(game,
                BullshitEventType.DISCARD.toString(),
                new DiscardEventData(playerId.id(), claimed.label(), cards.size()),
                "Player " + playerId.id() + " played " + cards.size() + " card(s) as " + claimed.label() + ".");
    }

    public void callBullshit(GameId gameId, PlayerId callerId)
            throws FinishedGameException, CannotCallBullshitException {
        Bullshit game = sessionService.getGame(gameId, Bullshit.class);
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
    }
}
