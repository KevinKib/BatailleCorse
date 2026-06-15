package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.presentation.ForfeitReason;
import org.kevinkib.cardgames.presentation.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.Seat;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;

public class BullshitLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final BullshitStateBroadcaster broadcaster;

    public BullshitLifecycleBroadcaster(BullshitStateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof Bullshit;
    }

    @Override
    public void disconnected(Game game, Seat seat, long deadlineEpochMs) {
        broadcaster.broadcast((Bullshit) game,
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(seat.playerId().id(), deadlineEpochMs),
                "Player " + seat.playerId().id() + " disconnected.");
    }

    @Override
    public void reconnected(Game game, Seat seat) {
        broadcaster.broadcast((Bullshit) game,
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(seat.playerId().id()),
                "Player " + seat.playerId().id() + " reconnected.");
    }

    @Override
    public void forfeited(Game game, Seat seat, ForfeitReason reason) {
        broadcaster.broadcast((Bullshit) game,
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId().id() + " forfeited.");
    }
}
