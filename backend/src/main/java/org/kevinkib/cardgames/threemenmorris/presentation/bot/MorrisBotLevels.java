package org.kevinkib.cardgames.threemenmorris.presentation.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The level key of each computer seat, read the way the coordinator reads it, for the game view. */
public final class MorrisBotLevels {

    private MorrisBotLevels() {
    }

    /** The default level when the host picked none, so that every computer seat has one. */
    public static Map<PlayerId, String> of(GameId gameId, Set<PlayerId> bots, BotSeatOptions seatOptions) {
        Map<PlayerId, String> levels = new HashMap<>();
        for (PlayerId seat : bots) {
            levels.put(seat, MorrisBotLevel.fromOption(
                    seatOptions.seatOptions(gameId, seat).get(MorrisBotCoordinator.LEVEL_KEY)).key());
        }
        return levels;
    }
}
