package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;

/** Per-game broadcaster for lifecycle events. The contract names what happened, never the state shape. */
public interface GameLifecycleBroadcaster {

    boolean supports(Game game);

    void disconnected(Game game, PlayerId player, long deadlineEpochMs);

    void reconnected(Game game, PlayerId player);

    void forfeited(Game game, PlayerId player, ForfeitReason reason);

    /**
     * A rematch started because {@code triggeredBy} left for good after every other expected player
     * had already asked for it. {@code fresh} is the newly dealt game. Optional: games without a
     * rematch flow ignore it.
     */
    default void rematchStarted(Game fresh, PlayerId triggeredBy) {
    }
}
