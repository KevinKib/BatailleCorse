package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

public class ThreeMenMorrisFactory implements GameFactory {

    public static final String GAME_TYPE = "three-men-morris";
    public static final String VARIANT_KEY = "variant";
    public static final String HOST_COLOUR_KEY = "hostColour";
    public static final String LEVEL_KEY = "level";

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    private final RandomGenerator random;

    public ThreeMenMorrisFactory() {
        this(RandomGenerator.getDefault());
    }

    /** The generator is drawn once per game, and only when the host colour is {@code random}. */
    public ThreeMenMorrisFactory(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        if (nbPlayers != ThreeMenMorris.PLAYERS) {
            throw new IllegalArgumentException("Three Men's Morris is played by exactly "
                    + ThreeMenMorris.PLAYERS + " players, not " + nbPlayers);
        }
        MorrisVariant variant = options.get(VARIANT_KEY).map(MorrisVariant::fromKey).orElse(MorrisVariant.DEFAULT);
        HostColour hostColour = options.get(HOST_COLOUR_KEY).map(HostColour::fromKey).orElse(HostColour.DEFAULT);
        return new ThreeMenMorris(id, variant.rules(), whiteSeat(hostColour));
    }

    private PlayerId whiteSeat(HostColour hostColour) {
        return switch (hostColour) {
            case WHITE -> HOST;
            case BLACK -> GUEST;
            case RANDOM -> random.nextInt(2) == 0 ? HOST : GUEST;
        };
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        return List.of(
                new OptionDescriptor(VARIANT_KEY, OptionType.CHOICE,
                        Arrays.stream(MorrisVariant.values()).map(MorrisVariant::key).toList(),
                        MorrisVariant.DEFAULT.key(), "options.threeMenMorris.variant"),
                new OptionDescriptor(HOST_COLOUR_KEY, OptionType.CHOICE,
                        Arrays.stream(HostColour.values()).map(HostColour::key).toList(),
                        HostColour.DEFAULT.key(), "options.threeMenMorris.hostColour"));
    }

    /** A computer seat plays at one of three levels, greedy unless the host picks another. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(MorrisBotLevel.values()).map(MorrisBotLevel::key).toList(),
                MorrisBotLevel.DEFAULT.key(), "options.threeMenMorris.level"));
    }
}
