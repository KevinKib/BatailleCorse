package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Creates Kilo de merde games for the session core: 3 to 10 players, a rounds option, computer seats with a level. */
public class KiloDeMerdeFactory implements GameFactory {

    public static final String GAME_TYPE = "kilo-de-merde";
    public static final String LEVEL_KEY = "level";

    private final DeckSource deckSource;

    public KiloDeMerdeFactory() {
        this(new RandomDeckSource());
    }

    /** @param deckSource where every random choice comes from (scripted under the {@code test} profile) */
    public KiloDeMerdeFactory(DeckSource deckSource) {
        this.deckSource = deckSource;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return KiloDeck.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return KiloDeck.MAX_PLAYERS;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(KiloDeMerdeBotLevel.values()).map(KiloDeMerdeBotLevel::key).toList(),
                KiloDeMerdeBotLevel.DEFAULT.key(), "options.kiloDeMerde.level"));
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new KiloDeMerde(id, nbPlayers, deckSource, KiloDeMerdeOptions.from(options));
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        KiloDeMerdeOptions.Rounds defaultRounds = KiloDeMerdeOptions.defaults().rounds();
        List<String> values = new ArrayList<>(List.of(defaultRounds.key()));
        Arrays.stream(KiloDeMerdeOptions.Rounds.values())
                .filter(rounds -> rounds != defaultRounds)
                .forEach(rounds -> values.add(rounds.key()));
        return List.of(new OptionDescriptor(KiloDeMerdeOptions.ROUNDS_KEY, OptionType.CHOICE, values,
                defaultRounds.key(), "options.kiloDeMerde.rounds"));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return KiloDeMerdeOptions.parseStrict(requested).toGameOptions();
    }
}
