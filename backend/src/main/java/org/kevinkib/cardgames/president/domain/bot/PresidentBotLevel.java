package org.kevinkib.cardgames.president.domain.bot;

import java.util.Arrays;

import static org.kevinkib.cardgames.president.domain.bot.PresidentBotTuning.ANY_HAND;

/**
 * The strength of a President computer seat, a seat option of the lobby ({@code level}). Self-play showed that
 * passing rather than breaking a set or spending a control card loses points, so the medium and hard levels
 * treat every hand as short; the easy level stays cautious from 7 cards and plays at random. A completion of the
 * four is taken by every level but easy (half of the time). Every level avoids a forbidden finish (going out on a
 * two) whenever another choice exists: it keeps a completion that would be one or that would strand its twos.
 */
public enum PresidentBotLevel {
    EASY("easy", new PresidentBotTuning(0.30, 0.20, 0, 2, 6, true, 0.5, true)),
    MEDIUM("medium", new PresidentBotTuning(0.0, 0.0, 3, 2, ANY_HAND, true, 1.0, true)),
    HARD("hard", new PresidentBotTuning(0.0, 0.0, 13, 2, ANY_HAND, true, 1.0, true));

    public static final PresidentBotLevel DEFAULT = MEDIUM;

    private final String key;
    private final PresidentBotTuning tuning;

    PresidentBotLevel(String key, PresidentBotTuning tuning) {
        this.key = key;
        this.tuning = tuning;
    }

    public String key() {
        return key;
    }

    public PresidentBotTuning tuning() {
        return tuning;
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static PresidentBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}
