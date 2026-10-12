package org.kevinkib.cardgames.presentation.state;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

/**
 * Lifecycle events (disconnection, reconnection, forfeit) of a seat game, sent to every seat as a state
 * broadcast. Bataille Corse keeps its own: its public DTO carries forfeit reasons.
 */
public class SeatLifecycleBroadcaster<G extends Game> implements GameLifecycleBroadcaster {

    private final Class<G> gameClass;
    private final SeatStateBroadcaster<G, ?> broadcaster;

    public SeatLifecycleBroadcaster(Class<G> gameClass, SeatStateBroadcaster<G, ?> broadcaster) {
        this.gameClass = gameClass;
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean supports(Game game) {
        return gameClass.isInstance(game);
    }

    @Override
    public void disconnected(Game game, PlayerId player, long deadlineEpochMs) {
        broadcaster.broadcast(gameClass.cast(game),
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(player.id(), deadlineEpochMs),
                "Player " + player.id() + " disconnected.");
    }

    @Override
    public void reconnected(Game game, PlayerId player) {
        broadcaster.broadcast(gameClass.cast(game),
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(player.id()),
                "Player " + player.id() + " reconnected.");
    }

    @Override
    public void forfeited(Game game, PlayerId player, ForfeitReason reason) {
        broadcaster.broadcast(gameClass.cast(game),
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(player.id(), reason.name()),
                "Player " + player.id() + " forfeited.");
    }
}
