package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Set;

/** Port: which seats of a game are held by computer players (a session fact, answered by the session core). */
@FunctionalInterface
public interface BotSeats {

    Set<PlayerId> botSeats(GameId gameId);

    /** For games without bots, and for tests. */
    static BotSeats none() {
        return gameId -> Set.of();
    }
}
