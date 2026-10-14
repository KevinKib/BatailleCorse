package org.kevinkib.cardgames.threemenmorris.domain.bot;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/** The strength of a computer seat, a seat option of the lobby ({@code level}). */
public enum MorrisBotLevel {
    RANDOM("random"),
    GREEDY("greedy"),
    PERFECT("perfect");

    public static final MorrisBotLevel DEFAULT = GREEDY;

    private final String key;

    MorrisBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The strategy of this level; equal moves are chosen through the generator. */
    public MorrisBot bot(RandomGenerator random) {
        return switch (this) {
            case RANDOM -> new RandomBot(random);
            case GREEDY -> new GreedyBot(random);
            case PERFECT -> new PerfectBot(random);
        };
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static MorrisBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}
