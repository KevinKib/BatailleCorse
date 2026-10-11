package org.kevinkib.cardgames.bataillecorse.domain.bot;

/**
 * How fast a computer seat reacts: the nine tiers of the former local AI, with the same reaction times
 * (frontend {@code model/Difficulty.ts}). The delay before each move is the reaction time plus or minus
 * {@link #JITTER_MS}.
 */
public enum BatailleCorseBotTier {
    TRAINING(2100),
    BRONZE(1800),
    SILVER(1500),
    GOLD(1200),
    PLATINUM(900),
    DIAMOND(700),
    CHAMPION(600),
    CHALLENGER(500),
    LEGEND(400);

    /** The delay varies by up to this many milliseconds around the reaction time. */
    public static final int JITTER_MS = 100;

    public static final BatailleCorseBotTier DEFAULT = BRONZE;

    private final int reactionTimeMs;

    BatailleCorseBotTier(int reactionTimeMs) {
        this.reactionTimeMs = reactionTimeMs;
    }

    public int reactionTimeMs() {
        return reactionTimeMs;
    }

    public int index() {
        return ordinal();
    }

    /** The tier named by a seat option value ("0" to "8"); anything absent or unreadable gives the default. */
    public static BatailleCorseBotTier fromOption(String value) {
        if (value == null) {
            return DEFAULT;
        }
        try {
            int index = Integer.parseInt(value);
            return index >= 0 && index < values().length ? values()[index] : DEFAULT;
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }
}
