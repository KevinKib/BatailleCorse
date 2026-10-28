package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotLevel;

import java.util.Arrays;
import java.util.List;

/** Creates 99 games for the session core: 2 to 12 players, host options, computer seats with a level. */
public class NinetyNineFactory implements GameFactory {

    public static final String GAME_TYPE = "ninety-nine";
    public static final String LEVEL_KEY = "level";

    private final DeckSource deckSource;

    public NinetyNineFactory() {
        this(new RandomDeckSource());
    }

    /** @param deckSource where every random choice comes from (scripted under the {@code test} profile) */
    public NinetyNineFactory(DeckSource deckSource) {
        this.deckSource = deckSource;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return NinetyNine.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return NinetyNine.MAX_PLAYERS;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(NinetyNineBotLevel.values()).map(NinetyNineBotLevel::key).toList(),
                NinetyNineBotLevel.DEFAULT.key(), "options.ninetyNine.level"));
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new NinetyNine(id, nbPlayers, deckSource, NinetyNineOptions.from(options));
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        return List.of(
                new OptionDescriptor(NinetyNineOptions.LIVES_KEY, OptionType.CHOICE,
                        Arrays.stream(NinetyNineOptions.Lives.values()).map(NinetyNineOptions.Lives::key).toList(),
                        NinetyNineOptions.defaults().lives().key(), "options.ninetyNine.lives"),
                new OptionDescriptor(NinetyNineOptions.QUICK_KEY, OptionType.CHOICE,
                        Arrays.stream(NinetyNineOptions.QuickMode.values())
                                .map(NinetyNineOptions.QuickMode::key).toList(),
                        NinetyNineOptions.defaults().quick().key(), "options.ninetyNine.quick"));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return NinetyNineOptions.parseStrict(requested).toGameOptions();
    }
}
