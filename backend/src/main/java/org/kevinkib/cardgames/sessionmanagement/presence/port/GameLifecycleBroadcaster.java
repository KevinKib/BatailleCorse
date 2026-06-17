package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;

/** Per-game broadcaster for lifecycle events. The contract names what happened, never the state shape. */
public interface GameLifecycleBroadcaster {

    boolean supports(Game game);

    void disconnected(Game game, PlayerId player, long deadlineEpochMs);

    void reconnected(Game game, PlayerId player);

    void forfeited(Game game, PlayerId player, ForfeitReason reason);
}
