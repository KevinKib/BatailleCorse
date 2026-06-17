package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Set;

/** Published query: which seats are eligible to act now (connected and not forfeited). */
public interface SeatPresence {
    Set<PlayerId> activeSeats(GameId gameId);
}
