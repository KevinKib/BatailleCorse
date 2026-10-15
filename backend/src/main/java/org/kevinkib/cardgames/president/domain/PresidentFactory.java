package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.ChoiceOption;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.FirstLead;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.Scoring;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Creates President games for the session core: 3 to 8 players, host options, no computer seat yet. */
public class PresidentFactory implements GameFactory {

    public static final String GAME_TYPE = "president";

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
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new President(id, nbPlayers, new Random().nextLong(), deckSource, PresidentOptions.from(options));
    }

    /** The round and match keys, default first; the variant keys are declared by the PRs that ship them. */
    @Override
    public List<OptionDescriptor> optionDescriptors() {
        PresidentOptions defaults = PresidentOptions.defaults();
        return List.of(
                descriptor(PresidentOptions.AFTER_PASS_KEY, AfterPass.values(), defaults.afterPass()),
                descriptor(PresidentOptions.FIRST_LEAD_KEY, FirstLead.values(), defaults.firstLead()),
                descriptor(PresidentOptions.EXCHANGE_KEY, ExchangeAmount.values(), defaults.exchange()),
                descriptor(PresidentOptions.GIVE_BACK_KEY, GiveBack.values(), defaults.giveBack()),
                descriptor(PresidentOptions.NEXT_LEAD_KEY, NextLead.values(), defaults.nextLead()),
                descriptor(PresidentOptions.SCORING_KEY, Scoring.values(), defaults.scoring()),
                descriptor(PresidentOptions.LENGTH_KEY, MatchLength.values(), defaults.length()));
    }

    @Override
    public GameOptions validate(GameOptions requested) {
        return PresidentOptions.parseStrict(requested).toGameOptions();
    }

    private static <E extends Enum<E> & ChoiceOption> OptionDescriptor descriptor(String key, E[] values,
                                                                                  E defaultValue) {
        return new OptionDescriptor(key, OptionType.CHOICE,
                Arrays.stream(values).map(ChoiceOption::key).toList(), defaultValue.key(),
                "options.president." + key);
    }
}
