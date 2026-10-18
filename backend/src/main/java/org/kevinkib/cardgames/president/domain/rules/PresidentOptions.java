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
 * <p>The round and match keys (the first six) and {@code mustPlay} are declared to the lobby and parsed; the other
 * variant keys exist as their "off" value and get their parsing, descriptor and behaviour from the PR that ships
 * them (V3, V4). Equal play, the king of hearts lead of round 1, the forbidden finish (a last play holding a 2
 * gives no place), the 2 that closes the trick and the quad that closes it are fixed rules, not options: their
 * former keys are unknown keys. The on/off keys travel as {@code "on"} and {@code "off"}.
 */
public record PresidentOptions(
        AfterPass afterPass,
        ExchangeAmount exchange,
        GiveBack giveBack,
        NextLead nextLead,
        Scoring scoring,
        MatchLength length,
        boolean mustPlay,
        Revolution revolution,
        Ranking ranking,
        Runs runs,
        boolean suitLock) {

    public static final String AFTER_PASS_KEY = "afterPass";
    public static final String EXCHANGE_KEY = "exchange";
    public static final String GIVE_BACK_KEY = "giveBack";
    public static final String NEXT_LEAD_KEY = "nextLead";
    public static final String SCORING_KEY = "scoring";
    public static final String LENGTH_KEY = "length";
    public static final String MUST_PLAY_KEY = "mustPlay";

    /** The keys the lobby may set, in display order. */
    public static final List<String> KEYS = List.of(AFTER_PASS_KEY, EXCHANGE_KEY, GIVE_BACK_KEY,
            NEXT_LEAD_KEY, SCORING_KEY, LENGTH_KEY, MUST_PLAY_KEY);
    private static final String ON = "on";
    private static final String OFF = "off";

    public static PresidentOptions defaults() {
        return new PresidentOptions(
                AfterPass.REENTER,
                ExchangeAmount.TWO, GiveBack.FREE, NextLead.SCUM, Scoring.RANK, MatchLength.ROUNDS_5,
                false,
                Revolution.OFF, Ranking.TWO_HIGH, Runs.OFF, false);
    }

    /** Lenient parse for room creation: an unknown value falls back to the default of its key. */
    public static PresidentOptions from(GameOptions options) {
        PresidentOptions d = defaults();
        return d.withAfterPass(lenient(options, AFTER_PASS_KEY, AfterPass.class, d.afterPass()))
                .withExchange(lenient(options, EXCHANGE_KEY, ExchangeAmount.class, d.exchange()))
                .withGiveBack(lenient(options, GIVE_BACK_KEY, GiveBack.class, d.giveBack()))
                .withNextLead(lenient(options, NEXT_LEAD_KEY, NextLead.class, d.nextLead()))
                .withScoring(lenient(options, SCORING_KEY, Scoring.class, d.scoring()))
                .withLength(lenient(options, LENGTH_KEY, MatchLength.class, d.length()))
                .withMustPlay(lenientFlag(options, MUST_PLAY_KEY, d.mustPlay()));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused instead of falling back to a default; a
     * missing key takes its default. The variants not shipped yet, the pruned options and the former keys of the
     * fixed rules are unknown keys.
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
                .withExchange(strict(options, EXCHANGE_KEY, ExchangeAmount.class, d.exchange()))
                .withGiveBack(strict(options, GIVE_BACK_KEY, GiveBack.class, d.giveBack()))
                .withNextLead(strict(options, NEXT_LEAD_KEY, NextLead.class, d.nextLead()))
                .withScoring(strict(options, SCORING_KEY, Scoring.class, d.scoring()))
                .withLength(strict(options, LENGTH_KEY, MatchLength.class, d.length()))
                .withMustPlay(strictFlag(options, MUST_PLAY_KEY, d.mustPlay()));
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

    private static boolean lenientFlag(GameOptions options, String key, boolean fallback) {
        return options.get(key).map(value -> ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null)
                .orElse(fallback);
    }

    private static boolean strictFlag(GameOptions options, String key, boolean fallback) {
        return options.get(key)
                .map(value -> {
                    if (ON.equals(value)) {
                        return true;
                    }
                    if (OFF.equals(value)) {
                        return false;
                    }
                    throw new InvalidOptionException("Invalid value " + value + " for option " + key);
                })
                .orElse(fallback);
    }

    /** The full option map of the declared keys, every one set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AFTER_PASS_KEY, afterPass.key());
        values.put(EXCHANGE_KEY, exchange.key());
        values.put(GIVE_BACK_KEY, giveBack.key());
        values.put(NEXT_LEAD_KEY, nextLead.key());
        values.put(SCORING_KEY, scoring.key());
        values.put(LENGTH_KEY, length.key());
        values.put(MUST_PLAY_KEY, mustPlay ? ON : OFF);
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

    public PresidentOptions withMustPlay(boolean value) {
        return with(b -> b.mustPlay = value);
    }

    /** A mutable copy used by the withers, so each one names only the key it changes. */
    private static final class Builder {
        AfterPass afterPass;
        ExchangeAmount exchange;
        GiveBack giveBack;
        NextLead nextLead;
        Scoring scoring;
        MatchLength length;
        boolean mustPlay;
        private final PresidentOptions from;

        Builder(PresidentOptions from) {
            this.from = from;
            this.afterPass = from.afterPass;
            this.exchange = from.exchange;
            this.giveBack = from.giveBack;
            this.nextLead = from.nextLead;
            this.scoring = from.scoring;
            this.length = from.length;
            this.mustPlay = from.mustPlay;
        }

        PresidentOptions build() {
            return new PresidentOptions(afterPass, exchange, giveBack, nextLead, scoring, length, mustPlay,
                    from.revolution, from.ranking, from.runs, from.suitLock);
        }
    }
}
