package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Builds Mangola games from the host's options: {@code setup} (free placement by default, or a preset board)
 * and {@code firstPlayer}. Every random choice comes from the injected generator, drawn once at creation
 * (the starting seat first, then the random set-up), so the aggregate itself stays deterministic.
 */
public class MangolaFactory implements GameFactory {

    public static final String GAME_TYPE = "mangola";
    public static final String SETUP_KEY = "setup";
    public static final String FIRST_PLAYER_KEY = "firstPlayer";
    /** The seat option that carries the level of a computer seat. */
    public static final String LEVEL_KEY = "level";

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    private final RandomGenerator random;

    public MangolaFactory() {
        this(RandomGenerator.getDefault());
    }

    public MangolaFactory(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return Mangola.PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return Mangola.PLAYERS;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    /** A computer seat plays at one of three levels, greedy (Medium) unless the host picks another. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(MangolaBotLevel.values()).map(MangolaBotLevel::key).toList(),
                MangolaBotLevel.DEFAULT.key(), "options.mangola.level"));
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        if (nbPlayers != Mangola.PLAYERS) {
            throw new IllegalArgumentException("Mangola is played by exactly " + Mangola.PLAYERS
                    + " players, not " + nbPlayers);
        }
        MangolaSetup setup = options.get(SETUP_KEY).map(MangolaSetup::fromKey).orElse(MangolaSetup.DEFAULT);
        FirstPlayer firstPlayer = options.get(FIRST_PLAYER_KEY).map(FirstPlayer::fromKey).orElse(FirstPlayer.DEFAULT);
        PlayerId seatA = seatA(firstPlayer);
        MangolaRules rules = MangolaRules.OWNER.withSetup(setup);
        if (setup == MangolaSetup.FREE) {
            return new Mangola(id, rules, seatA);
        }
        return Mangola.fromLayout(id, rules, seatA, setup.board(random));
    }

    private PlayerId seatA(FirstPlayer firstPlayer) {
        return switch (firstPlayer) {
            case HOST -> HOST;
            case GUEST -> GUEST;
            case RANDOM -> random.nextInt(2) == 0 ? HOST : GUEST;
        };
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        return List.of(
                new OptionDescriptor(SETUP_KEY, OptionType.CHOICE,
                        Arrays.stream(MangolaSetup.values()).map(MangolaSetup::key).toList(),
                        MangolaSetup.DEFAULT.key(), "options.mangola.setup"),
                new OptionDescriptor(FIRST_PLAYER_KEY, OptionType.CHOICE,
                        Arrays.stream(FirstPlayer.values()).map(FirstPlayer::key).toList(),
                        FirstPlayer.DEFAULT.key(), "options.mangola.firstPlayer"));
    }
}
