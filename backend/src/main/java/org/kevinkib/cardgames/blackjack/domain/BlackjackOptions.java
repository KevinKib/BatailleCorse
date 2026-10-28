package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The host options of a Blackjack game, as data: the number of hands and the largest bet (spec section 4.1). The
 * starting stack and the smallest bet are fixed.
 */
public record BlackjackOptions(Hands hands, MaxBet maxBet) {

    /** Points each player starts with. */
    public static final int STARTING_STACK = 100;

    /** The smallest bet of a hand, in points. */
    public static final int MIN_BET = 1;

    public static final String HANDS_KEY = "hands";
    public static final String MAX_BET_KEY = "maxBet";

    /** The keys the lobby may set, in display order. */
    public static final List<String> KEYS = List.of(HANDS_KEY, MAX_BET_KEY);

    /** Number of hands in a game; the first constant is the default. */
    public enum Hands {
        TEN(10), FIVE(5), FIFTEEN(15), TWENTY(20);

        private final int count;

        Hands(int count) {
            this.count = count;
        }

        public int count() {
            return count;
        }

        public String key() {
            return Integer.toString(count);
        }

        static Optional<Hands> find(String value) {
            for (Hands hands : values()) {
                if (hands.key().equals(value)) {
                    return Optional.of(hands);
                }
            }
            return Optional.empty();
        }
    }

    /** The largest bet of a hand (also capped by the stack); the first constant is the default. */
    public enum MaxBet {
        FIFTY(50), TWENTY_FIVE(25), HUNDRED(100);

        private final int amount;

        MaxBet(int amount) {
            this.amount = amount;
        }

        public int amount() {
            return amount;
        }

        public String key() {
            return Integer.toString(amount);
        }

        static Optional<MaxBet> find(String value) {
            for (MaxBet maxBet : values()) {
                if (maxBet.key().equals(value)) {
                    return Optional.of(maxBet);
                }
            }
            return Optional.empty();
        }
    }

    public static BlackjackOptions defaults() {
        return new BlackjackOptions(Hands.TEN, MaxBet.FIFTY);
    }

    /** Lenient parse for room creation: an unknown value falls back to the default of its key. */
    public static BlackjackOptions from(GameOptions options) {
        BlackjackOptions d = defaults();
        return new BlackjackOptions(
                options.get(HANDS_KEY).flatMap(Hands::find).orElse(d.hands),
                options.get(MAX_BET_KEY).flatMap(MaxBet::find).orElse(d.maxBet));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused; a missing key takes its default.
     *
     * @throws InvalidOptionException on an unknown key or value
     */
    public static BlackjackOptions parseStrict(GameOptions options) {
        for (String key : options.values().keySet()) {
            if (!KEYS.contains(key)) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        BlackjackOptions d = defaults();
        Hands hands = options.get(HANDS_KEY)
                .map(v -> Hands.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + HANDS_KEY)))
                .orElse(d.hands);
        MaxBet maxBet = options.get(MAX_BET_KEY)
                .map(v -> MaxBet.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + MAX_BET_KEY)))
                .orElse(d.maxBet);
        return new BlackjackOptions(hands, maxBet);
    }

    /** The full option map, every key set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(HANDS_KEY, hands.key());
        values.put(MAX_BET_KEY, maxBet.key());
        return GameOptions.of(values);
    }
}
