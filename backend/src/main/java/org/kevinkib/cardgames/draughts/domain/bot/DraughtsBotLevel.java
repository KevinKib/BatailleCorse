package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/** The strength of a computer seat, a seat option of the lobby ({@code level}): Easy, Medium (default), Expert. */
public enum DraughtsBotLevel {
    RANDOM("random"),
    GREEDY("greedy"),
    EXPERT("expert");

    public static final DraughtsBotLevel DEFAULT = GREEDY;

    private final String key;

    DraughtsBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The strategy of this level; ties go through the generator, the expert spends the budget. */
    public DraughtsBot bot(RandomGenerator random, ExpertDraughtsBot.Budget budget) {
        return switch (this) {
            case RANDOM -> new RandomDraughtsBot(random);
            case GREEDY -> new GreedyDraughtsBot(random);
            case EXPERT -> new ExpertDraughtsBot(random, budget);
        };
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static DraughtsBotLevel fromOption(String value) {
        return Arrays.stream(values()).filter(level -> level.key.equals(value)).findFirst().orElse(DEFAULT);
    }
}
