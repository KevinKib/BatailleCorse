package org.kevinkib.cardgames.ninetynine.domain.bot;

import java.util.Random;

/** The strategy of each level. */
public final class NinetyNineBots {

    private NinetyNineBots() {
    }

    /** @param random the random source of the {@code easy} and {@code hard} levels (seeded in tests) */
    public static NinetyNineBotStrategy forLevel(NinetyNineBotLevel level, Random random) {
        return switch (level) {
            case EASY -> new RandomNinetyNineBot(random);
            case MEDIUM -> new GreedyNinetyNineBot();
            case HARD -> new SamplingNinetyNineBot(random);
        };
    }
}
