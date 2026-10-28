package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The host options of a Kilo de merde game, as data: the number of rounds (spec section 6.1).
 */
public record KiloDeMerdeOptions(Rounds rounds) {

    public static final String ROUNDS_KEY = "rounds";

    /** The keys the lobby may set, in display order. */
    public static final List<String> KEYS = List.of(ROUNDS_KEY);

    /** The number of rounds of a game; {@link #TEN} is the default. */
    public enum Rounds {
        FIVE(5), TEN(10), FIFTEEN(15), TWENTY(20);

        private final int count;

        Rounds(int count) {
            this.count = count;
        }

        public int count() {
            return count;
        }

        public String key() {
            return Integer.toString(count);
        }

        static Optional<Rounds> find(String value) {
            for (Rounds rounds : values()) {
                if (rounds.key().equals(value)) {
                    return Optional.of(rounds);
                }
            }
            return Optional.empty();
        }
    }

    public static KiloDeMerdeOptions defaults() {
        return new KiloDeMerdeOptions(Rounds.TEN);
    }

    /** Lenient parse for room creation: an unknown value falls back to the default. */
    public static KiloDeMerdeOptions from(GameOptions options) {
        return new KiloDeMerdeOptions(options.get(ROUNDS_KEY).flatMap(Rounds::find).orElse(defaults().rounds));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused; a missing key takes its default.
     *
     * @throws InvalidOptionException on an unknown key or value
     */
    public static KiloDeMerdeOptions parseStrict(GameOptions options) {
        for (String key : options.values().keySet()) {
            if (!KEYS.contains(key)) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        Rounds rounds = options.get(ROUNDS_KEY)
                .map(v -> Rounds.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + ROUNDS_KEY)))
                .orElse(defaults().rounds);
        return new KiloDeMerdeOptions(rounds);
    }

    /** The full option map, every key set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ROUNDS_KEY, rounds.key());
        return GameOptions.of(values);
    }
}
