package org.kevinkib.cardgames.mangola.domain.bot;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/** The strength of a computer seat, a seat option of the lobby ({@code level}): Easy, Medium (default), Expert. */
public enum MangolaBotLevel {
    RANDOM("random"),
    GREEDY("greedy"),
    EXPERT("expert");

    public static final MangolaBotLevel DEFAULT = GREEDY;

    private final String key;

    MangolaBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The strategy of this level; equal moves are chosen through the generator, the expert spends the budget. */
    public MangolaBot bot(RandomGenerator random, ExpertMangolaBot.Budget budget) {
        return switch (this) {
            case RANDOM -> new RandomMangolaBot(random);
            case GREEDY -> new GreedyMangolaBot(random);
            case EXPERT -> new ExpertMangolaBot(random, budget);
        };
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static MangolaBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}
