package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;

/** Port: the display name each seat of a game chose when it joined (a session fact, answered by the session core). */
@FunctionalInterface
public interface SeatNames {

    Map<PlayerId, String> seatNames(GameId gameId);

    /** For tests and callers that do not care about names. */
    static SeatNames none() {
        return gameId -> Map.of();
    }
}
