package org.kevinkib.cardgames.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;

/** Port: the options set on a computer seat (for example its tier), a session fact answered by the session core. */
@FunctionalInterface
public interface BotSeatOptions {

    Map<String, String> seatOptions(GameId gameId, PlayerId seat);

    /** For games whose bot seats have no option, and for tests. */
    static BotSeatOptions none() {
        return (gameId, seat) -> Map.of();
    }
}
