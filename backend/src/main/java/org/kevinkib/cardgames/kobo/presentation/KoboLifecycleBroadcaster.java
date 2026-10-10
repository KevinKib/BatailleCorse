package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

public class KoboLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final KoboStateBroadcaster broadcaster;

    public KoboLifecycleBroadcaster(KoboStateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof Kobo;
    }

    @Override
    public void disconnected(Game game, PlayerId player, long deadlineEpochMs) {
        broadcaster.broadcast((Kobo) game,
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(player.id(), deadlineEpochMs),
                "Player " + player.id() + " disconnected.");
    }

    @Override
    public void reconnected(Game game, PlayerId player) {
        broadcaster.broadcast((Kobo) game,
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(player.id()),
                "Player " + player.id() + " reconnected.");
    }

    @Override
    public void forfeited(Game game, PlayerId player, ForfeitReason reason) {
        broadcaster.broadcast((Kobo) game,
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(player.id()),
                "Player " + player.id() + " forfeited.");
    }
}
