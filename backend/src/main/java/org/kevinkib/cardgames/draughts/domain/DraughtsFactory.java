package org.kevinkib.cardgames.draughts.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBotLevel;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;

public class DraughtsFactory implements GameFactory {

    public static final String GAME_TYPE = "draughts";
    public static final String HOST_COLOUR_KEY = "hostColour";
    /** Retired lobby option (draw offers are always allowed): a client may still send it, it is ignored. */
    private static final String LEGACY_DRAW_OFFERS_KEY = "drawOffers";
    public static final String LEVEL_KEY = "level";

    /** The levels of a computer seat, weakest first; Medium (greedy) unless the host picks another. */
    public static final List<String> LEVELS = java.util.Arrays.stream(DraughtsBotLevel.values())
            .map(DraughtsBotLevel::key).toList();
    public static final String DEFAULT_LEVEL = DraughtsBotLevel.DEFAULT.key();

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    private final RandomGenerator random;

    public DraughtsFactory() {
        this(RandomGenerator.getDefault());
    }

    /** The generator is drawn once per game, and only when the host colour is {@code random}. */
    public DraughtsFactory(RandomGenerator random) {
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
        return Draughts.PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return Draughts.PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        if (nbPlayers != Draughts.PLAYERS) {
            throw new IllegalArgumentException("Draughts is played by exactly " + Draughts.PLAYERS
                    + " players, not " + nbPlayers);
        }
        HostColour hostColour = options.get(HOST_COLOUR_KEY).map(HostColour::fromKey).orElse(HostColour.DEFAULT);
        return new Draughts(id, whiteSeat(hostColour));
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
                new OptionDescriptor(HOST_COLOUR_KEY, OptionType.CHOICE,
                        java.util.Arrays.stream(HostColour.values()).map(HostColour::key).toList(),
                        HostColour.DEFAULT.key(), "options.draughts.hostColour"));
    }

    /** Strict like every game, except that the retired {@code drawOffers} key is dropped instead of refused. */
    @Override
    public GameOptions validate(GameOptions requested) {
        Map<String, String> current = new LinkedHashMap<>(requested.values());
        current.remove(LEGACY_DRAW_OFFERS_KEY);
        return GameFactory.super.validate(GameOptions.of(current));
    }

    /** A computer seat plays at one of three levels. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE, LEVELS, DEFAULT_LEVEL,
                "options.draughts.level"));
    }
}
