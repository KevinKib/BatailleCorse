package org.kevinkib.cardgames.mangola.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The level key of each computer seat, read the way the coordinator reads it, for the game view. */
public final class MangolaBotLevels {

    private MangolaBotLevels() {
    }

    /** The default level when the host picked none, so that every computer seat has one. */
    public static Map<PlayerId, String> of(GameId gameId, Set<PlayerId> bots, BotSeatOptions seatOptions) {
        Map<PlayerId, String> levels = new HashMap<>();
        for (PlayerId seat : bots) {
            levels.put(seat, MangolaBotLevel.fromOption(
                    seatOptions.seatOptions(gameId, seat).get(MangolaBotCoordinator.LEVEL_KEY)).key());
        }
        return levels;
    }
}
