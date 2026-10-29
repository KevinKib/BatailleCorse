package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotLevel;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Creates Blackjack games for the session core: 1 to 6 players (one seat is a solo game against the dealer), the
 * {@code hands} and {@code maxBet} options, computer seats with a level.
 */
public class BlackjackFactory implements GameFactory {

    public static final String GAME_TYPE = "blackjack";
    public static final String LEVEL_KEY = "level";

    private final ShoeSource shoeSource;

    public BlackjackFactory() {
        this(new RandomShoeSource());
    }

    /** @param shoeSource where every shuffle comes from (scripted under the {@code test} profile) */
    public BlackjackFactory(ShoeSource shoeSource) {
        this.shoeSource = shoeSource;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return Blackjack.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return Blackjack.MAX_PLAYERS;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(BlackjackBotLevel.values()).map(BlackjackBotLevel::key).toList(),
                BlackjackBotLevel.DEFAULT.key(), "options.blackjack.level"));
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new Blackjack(id, nbPlayers, shoeSource, BlackjackOptions.from(options));
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        BlackjackOptions defaults = BlackjackOptions.defaults();
        return List.of(
                descriptor(BlackjackOptions.HANDS_KEY, BlackjackOptions.Hands.values(), defaults.hands(),
                        BlackjackOptions.Hands::key),
                descriptor(BlackjackOptions.MAX_BET_KEY, BlackjackOptions.MaxBet.values(), defaults.maxBet(),
                        BlackjackOptions.MaxBet::key));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return BlackjackOptions.parseStrict(requested).toGameOptions();
    }

    /** The values in ascending (declaration) order; the default is carried separately, not by position. */
    private static <T> OptionDescriptor descriptor(String key, T[] values, T defaultValue, Function<T, String> label) {
        List<String> keys = Arrays.stream(values).map(label).toList();
        return new OptionDescriptor(key, OptionType.CHOICE, keys, label.apply(defaultValue),
                "options.blackjack." + key);
    }
}
