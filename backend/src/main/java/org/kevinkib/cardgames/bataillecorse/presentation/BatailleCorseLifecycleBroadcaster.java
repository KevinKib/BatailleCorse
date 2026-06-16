package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;

public class BatailleCorseLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final GameMessagingService messaging;
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public BatailleCorseLifecycleBroadcaster(GameMessagingService messaging,
                                             ForfeitReasonRegistry forfeitReasonRegistry) {
        this.messaging = messaging;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof BatailleCorse;
    }

    @Override
    public void disconnected(Game game, Seat seat, long deadlineEpochMs) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(seat.playerId().id(), deadlineEpochMs),
                "Player " + seat.playerId() + " disconnected.",
                BatailleCorseDto.from(bc)));
    }

    @Override
    public void reconnected(Game game, Seat seat) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " reconnected.",
                BatailleCorseDto.from(bc)));
    }

    @Override
    public void forfeited(Game game, Seat seat, ForfeitReason reason) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(bc, forfeitReasonRegistry.reasonsBySeat(seat.gameId()))));
    }
}
