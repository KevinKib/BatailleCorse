package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

public class ThreeMenMorrisLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final ThreeMenMorrisStateBroadcaster broadcaster;

    public ThreeMenMorrisLifecycleBroadcaster(ThreeMenMorrisStateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof ThreeMenMorris;
    }

    @Override
    public void disconnected(Game game, PlayerId player, long deadlineEpochMs) {
        broadcaster.broadcast((ThreeMenMorris) game,
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(player.id(), deadlineEpochMs),
                "Player " + player.id() + " disconnected.");
    }

    @Override
    public void reconnected(Game game, PlayerId player) {
        broadcaster.broadcast((ThreeMenMorris) game,
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(player.id()),
                "Player " + player.id() + " reconnected.");
    }

    @Override
    public void forfeited(Game game, PlayerId player, ForfeitReason reason) {
        broadcaster.broadcast((ThreeMenMorris) game,
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(player.id(), reason.name()),
                "Player " + player.id() + " forfeited.");
    }
}
