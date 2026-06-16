package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

/** Per-game broadcaster for lifecycle events. The contract names what happened, never the state shape. */
public interface GameLifecycleBroadcaster {

    boolean supports(Game game);

    void disconnected(Game game, Seat seat, long deadlineEpochMs);

    void reconnected(Game game, Seat seat);

    void forfeited(Game game, Seat seat, ForfeitReason reason);
}
