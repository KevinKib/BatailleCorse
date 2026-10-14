package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.rules.KoboRules;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotLevel;

import java.util.Arrays;
import java.util.List;

public class KoboFactory implements GameFactory {

    public static final String GAME_TYPE = "kobo";
    /** The seat option that carries the level of a computer seat. */
    public static final String LEVEL_KEY = "level";

    private final DeckSource deckSource;

    public KoboFactory() {
        this(new RandomDeckSource());
    }

    public KoboFactory(DeckSource deckSource) {
        this.deckSource = deckSource;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return KoboRules.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return KoboRules.MAX_PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Kobo(id, nbPlayers, new java.util.Random().nextLong(), deckSource);
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    /** A computer seat plays at one of three levels, medium unless the host picks another. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(KoboBotLevel.values()).map(KoboBotLevel::key).toList(),
                KoboBotLevel.DEFAULT.key(), "options.kobo.level"));
    }
}
