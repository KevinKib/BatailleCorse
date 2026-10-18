package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotLevel;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.ChoiceOption;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.Scoring;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Creates President games for the session core: 3 to 8 players, host options, computer seats with a level. */
public class PresidentFactory implements GameFactory {

    public static final String GAME_TYPE = "president";
    public static final String LEVEL_KEY = "level";

    private final DeckSource deckSource;

    public PresidentFactory() {
        this(new RandomDeckSource());
    }

    /** @param deckSource where the cards of every round come from (scripted under the {@code test} profile) */
    public PresidentFactory(DeckSource deckSource) {
        this.deckSource = deckSource;
    }

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return President.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return President.MAX_PLAYERS;
    }

    @Override
    public boolean supportsBots() {
        return true;
    }

    /** A computer seat plays at one of three levels, medium unless the host picks another. */
    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor(LEVEL_KEY, OptionType.CHOICE,
                Arrays.stream(PresidentBotLevel.values()).map(PresidentBotLevel::key).toList(),
                PresidentBotLevel.DEFAULT.key(), "options.president.level"));
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new President(id, nbPlayers, new Random().nextLong(), deckSource, PresidentOptions.from(options));
    }

    /** The round and match keys and the must-play variant, default first; the other variant keys wait for their PRs. */
    @Override
    public List<OptionDescriptor> optionDescriptors() {
        PresidentOptions defaults = PresidentOptions.defaults();
        return List.of(
                descriptor(PresidentOptions.AFTER_PASS_KEY, AfterPass.values(), defaults.afterPass()),
                descriptor(PresidentOptions.EXCHANGE_KEY, ExchangeAmount.values(), defaults.exchange()),
                descriptor(PresidentOptions.GIVE_BACK_KEY, GiveBack.values(), defaults.giveBack()),
                descriptor(PresidentOptions.NEXT_LEAD_KEY, NextLead.values(), defaults.nextLead()),
                descriptor(PresidentOptions.SCORING_KEY, Scoring.values(), defaults.scoring()),
                descriptor(PresidentOptions.LENGTH_KEY, MatchLength.values(), defaults.length()),
                flag(PresidentOptions.MUST_PLAY_KEY, defaults.mustPlay()),
                flag(PresidentOptions.REDUCED_CUT_KEY, defaults.reducedCut()));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return PresidentOptions.parseStrict(requested).toGameOptions();
    }

    /** An on/off key: the default value comes first. */
    private static OptionDescriptor flag(String key, boolean defaultOn) {
        List<String> values = defaultOn ? List.of("on", "off") : List.of("off", "on");
        return new OptionDescriptor(key, OptionType.CHOICE, values, values.get(0), "options.president." + key);
    }

    private static <E extends Enum<E> & ChoiceOption> OptionDescriptor descriptor(String key, E[] values,
                                                                                  E defaultValue) {
        return new OptionDescriptor(key, OptionType.CHOICE,
                Arrays.stream(values).map(ChoiceOption::key).toList(), defaultValue.key(),
                "options.president." + key);
    }
}
