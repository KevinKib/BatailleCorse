package org.kevinkib.cardgames.president.domain.rules;

import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The host options in force, as data: the aggregate reads them and holds no rule switch of its own.
 *
 * <p>The sixteen keys of the spec exist here. The round and match keys (the first seven) and {@code equalPlay}
 * (V1) are declared to the lobby and parsed; the eight other variant keys exist as their "off" value and get
 * their parsing, descriptor and behaviour from the PR that ships them (V2 to V4). Until then {@link #defaults()}
 * holds them off, including {@code quadClears} and {@code forbiddenFinish}, whose Classic default (on) is set when
 * the rules exist.
 */
public record PresidentOptions(
        AfterPass afterPass,
        FirstLead firstLead,
        ExchangeAmount exchange,
        GiveBack giveBack,
        NextLead nextLead,
        Scoring scoring,
        MatchLength length,
        EqualPlay equalPlay,
        TwoCuts twoCuts,
        boolean quadClears,
        ForbiddenFinish forbiddenFinish,
        boolean mustPlay,
        Revolution revolution,
        Ranking ranking,
        Runs runs,
        boolean suitLock) {

    public static final String AFTER_PASS_KEY = "afterPass";
    public static final String FIRST_LEAD_KEY = "firstLead";
    public static final String EXCHANGE_KEY = "exchange";
    public static final String GIVE_BACK_KEY = "giveBack";
    public static final String NEXT_LEAD_KEY = "nextLead";
    public static final String SCORING_KEY = "scoring";
    public static final String LENGTH_KEY = "length";
    public static final String EQUAL_PLAY_KEY = "equalPlay";

    /** The keys the lobby may set, in display order. */
    public static final List<String> KEYS = List.of(AFTER_PASS_KEY, FIRST_LEAD_KEY, EXCHANGE_KEY, GIVE_BACK_KEY,
            NEXT_LEAD_KEY, SCORING_KEY, LENGTH_KEY, EQUAL_PLAY_KEY);

    public static PresidentOptions defaults() {
        return new PresidentOptions(
                AfterPass.REENTER, FirstLead.KING_OF_HEARTS,
                ExchangeAmount.TWO, GiveBack.FREE, NextLead.SCUM, Scoring.RANK, MatchLength.ROUNDS_5,
                EqualPlay.ON, TwoCuts.OFF, false, ForbiddenFinish.OFF, false,
                Revolution.OFF, Ranking.TWO_HIGH, Runs.OFF, false);
    }

    /** Lenient parse for room creation: an unknown value falls back to the default of its key. */
    public static PresidentOptions from(GameOptions options) {
        PresidentOptions d = defaults();
        return d.withAfterPass(lenient(options, AFTER_PASS_KEY, AfterPass.class, d.afterPass()))
                .withFirstLead(lenient(options, FIRST_LEAD_KEY, FirstLead.class, d.firstLead()))
                .withExchange(lenient(options, EXCHANGE_KEY, ExchangeAmount.class, d.exchange()))
                .withGiveBack(lenient(options, GIVE_BACK_KEY, GiveBack.class, d.giveBack()))
                .withNextLead(lenient(options, NEXT_LEAD_KEY, NextLead.class, d.nextLead()))
                .withScoring(lenient(options, SCORING_KEY, Scoring.class, d.scoring()))
                .withLength(lenient(options, LENGTH_KEY, MatchLength.class, d.length()))
                .withEqualPlay(lenient(options, EQUAL_PLAY_KEY, EqualPlay.class, d.equalPlay()));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused instead of falling back to a default; a
     * missing key takes its default. The variants not shipped yet and the pruned options are unknown keys.
     *
     * @throws InvalidOptionException on an unknown key or value
     */
    public static PresidentOptions parseStrict(GameOptions options) {
        for (String key : options.values().keySet()) {
            if (!KEYS.contains(key)) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        PresidentOptions d = defaults();
        return d.withAfterPass(strict(options, AFTER_PASS_KEY, AfterPass.class, d.afterPass()))
                .withFirstLead(strict(options, FIRST_LEAD_KEY, FirstLead.class, d.firstLead()))
                .withExchange(strict(options, EXCHANGE_KEY, ExchangeAmount.class, d.exchange()))
                .withGiveBack(strict(options, GIVE_BACK_KEY, GiveBack.class, d.giveBack()))
                .withNextLead(strict(options, NEXT_LEAD_KEY, NextLead.class, d.nextLead()))
                .withScoring(strict(options, SCORING_KEY, Scoring.class, d.scoring()))
                .withLength(strict(options, LENGTH_KEY, MatchLength.class, d.length()))
                .withEqualPlay(strict(options, EQUAL_PLAY_KEY, EqualPlay.class, d.equalPlay()));
    }

    private static <E extends Enum<E> & ChoiceOption> E lenient(GameOptions options, String key, Class<E> type,
                                                                E fallback) {
        return options.get(key).flatMap(value -> OptionKeys.find(type, value)).orElse(fallback);
    }

    private static <E extends Enum<E> & ChoiceOption> E strict(GameOptions options, String key, Class<E> type,
                                                               E fallback) {
        return options.get(key)
                .map(value -> OptionKeys.find(type, value).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + value + " for option " + key)))
                .orElse(fallback);
    }

    /** The full option map of the declared keys, every one set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AFTER_PASS_KEY, afterPass.key());
        values.put(FIRST_LEAD_KEY, firstLead.key());
        values.put(EXCHANGE_KEY, exchange.key());
        values.put(GIVE_BACK_KEY, giveBack.key());
        values.put(NEXT_LEAD_KEY, nextLead.key());
        values.put(SCORING_KEY, scoring.key());
        values.put(LENGTH_KEY, length.key());
        values.put(EQUAL_PLAY_KEY, equalPlay.key());
        return GameOptions.of(values);
    }

    private PresidentOptions with(Consumer<Builder> change) {
        Builder builder = new Builder(this);
        change.accept(builder);
        return builder.build();
    }

    public PresidentOptions withAfterPass(AfterPass value) {
        return with(b -> b.afterPass = value);
    }

    public PresidentOptions withFirstLead(FirstLead value) {
        return with(b -> b.firstLead = value);
    }

    public PresidentOptions withExchange(ExchangeAmount value) {
        return with(b -> b.exchange = value);
    }

    public PresidentOptions withGiveBack(GiveBack value) {
        return with(b -> b.giveBack = value);
    }

    public PresidentOptions withNextLead(NextLead value) {
        return with(b -> b.nextLead = value);
    }

    public PresidentOptions withScoring(Scoring value) {
        return with(b -> b.scoring = value);
    }

    public PresidentOptions withLength(MatchLength value) {
        return with(b -> b.length = value);
    }

    public PresidentOptions withEqualPlay(EqualPlay value) {
        return with(b -> b.equalPlay = value);
    }

    /** A mutable copy used by the withers, so each one names only the key it changes. */
    private static final class Builder {
        AfterPass afterPass;
        FirstLead firstLead;
        ExchangeAmount exchange;
        GiveBack giveBack;
        NextLead nextLead;
        Scoring scoring;
        MatchLength length;
        EqualPlay equalPlay;
        private final PresidentOptions from;

        Builder(PresidentOptions from) {
            this.from = from;
            this.afterPass = from.afterPass;
            this.firstLead = from.firstLead;
            this.exchange = from.exchange;
            this.giveBack = from.giveBack;
            this.nextLead = from.nextLead;
            this.scoring = from.scoring;
            this.length = from.length;
            this.equalPlay = from.equalPlay;
        }

        PresidentOptions build() {
            return new PresidentOptions(afterPass, firstLead, exchange, giveBack, nextLead, scoring, length,
                    equalPlay, from.twoCuts, from.quadClears, from.forbiddenFinish, from.mustPlay,
                    from.revolution, from.ranking, from.runs, from.suitLock);
        }
    }
}
