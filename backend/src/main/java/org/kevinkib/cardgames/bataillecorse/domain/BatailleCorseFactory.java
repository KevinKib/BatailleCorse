package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotTier;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.Arrays;
import java.util.List;

public class BatailleCorseFactory implements GameFactory {

    public static final String GAME_TYPE = "bataille-corse";
    public static final String TIER_KEY = "tier";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new BatailleCorse(id, nbPlayers);
    }

    /** A computer seat plays at one of nine tiers (0 slowest, 8 fastest), tier 1 unless the host picks another. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(TIER_KEY, OptionType.CHOICE,
                Arrays.stream(BatailleCorseBotTier.values()).map(tier -> String.valueOf(tier.index())).toList(),
                String.valueOf(BatailleCorseBotTier.DEFAULT.index()), "options.bataillecorse.tier"));
    }
}
