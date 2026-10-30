package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The host options of a 99 game, as data: lives per player (2 to 4) and quick mode (spec section 4.1), plus the
 * hidden free-play rule (on by default, not listed by the lobby, settable through the options API).
 */
public record NinetyNineOptions(Lives lives, QuickMode quick, FreePlay freePlay) {

    public static final String LIVES_KEY = "lives";
    public static final String QUICK_KEY = "quick";
    /** Hidden: not in the factory's option descriptors, so no lobby group, yet accepted by the strict parse. */
    public static final String FREE_PLAY_KEY = "freePlay";

    /** The keys the options API accepts, in storage order. */
    public static final List<String> KEYS = List.of(LIVES_KEY, QUICK_KEY, FREE_PLAY_KEY);

    /** Lives per player; the first constant is the default. */
    public enum Lives {
        THREE(3), TWO(2), FOUR(4);

        private final int count;

        Lives(int count) {
            this.count = count;
        }

        public int count() {
            return count;
        }

        public String key() {
            return Integer.toString(count);
        }

        static Optional<Lives> find(String value) {
            for (Lives lives : values()) {
                if (lives.key().equals(value)) {
                    return Optional.of(lives);
                }
            }
            return Optional.empty();
        }
    }

    /** Quick mode: the game ends at the first elimination and the others are ranked by lives left. */
    public enum QuickMode {
        OFF("off"), ON("on");

        private final String key;

        QuickMode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Optional<QuickMode> find(String value) {
            for (QuickMode mode : values()) {
                if (mode.key.equals(value)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Free play: a seat may play a card that takes the total over 99 (and an Ace as 11 that goes over) even while it
     * holds a safe card, and pays a life for it as a stuck seat does. Off, a seat holding a safe card must play one.
     */
    public enum FreePlay {
        ON("on"), OFF("off");

        private final String key;

        FreePlay(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Optional<FreePlay> find(String value) {
            for (FreePlay mode : values()) {
                if (mode.key.equals(value)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }

    public static NinetyNineOptions defaults() {
        return new NinetyNineOptions(Lives.THREE, QuickMode.OFF, FreePlay.ON);
    }

    /** Lenient parse for room creation: an unknown value falls back to the default of its key. */
    public static NinetyNineOptions from(GameOptions options) {
        NinetyNineOptions d = defaults();
        return new NinetyNineOptions(
                options.get(LIVES_KEY).flatMap(Lives::find).orElse(d.lives),
                options.get(QUICK_KEY).flatMap(QuickMode::find).orElse(d.quick),
                options.get(FREE_PLAY_KEY).flatMap(FreePlay::find).orElse(d.freePlay));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused; a missing key takes its default.
     *
     * @throws InvalidOptionException on an unknown key or value
     */
    public static NinetyNineOptions parseStrict(GameOptions options) {
        for (String key : options.values().keySet()) {
            if (!KEYS.contains(key)) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        NinetyNineOptions d = defaults();
        Lives lives = options.get(LIVES_KEY)
                .map(v -> Lives.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + LIVES_KEY)))
                .orElse(d.lives);
        QuickMode quick = options.get(QUICK_KEY)
                .map(v -> QuickMode.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + QUICK_KEY)))
                .orElse(d.quick);
        FreePlay freePlay = options.get(FREE_PLAY_KEY)
                .map(v -> FreePlay.find(v).orElseThrow(
                        () -> new InvalidOptionException("Invalid value " + v + " for option " + FREE_PLAY_KEY)))
                .orElse(d.freePlay);
        return new NinetyNineOptions(lives, quick, freePlay);
    }

    /** The full option map, every key set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(LIVES_KEY, lives.key());
        values.put(QUICK_KEY, quick.key());
        values.put(FREE_PLAY_KEY, freePlay.key());
        return GameOptions.of(values);
    }
}
