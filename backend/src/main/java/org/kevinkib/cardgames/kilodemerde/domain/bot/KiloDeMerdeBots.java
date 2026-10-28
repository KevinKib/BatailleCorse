package org.kevinkib.cardgames.kilodemerde.domain.bot;

import java.util.random.RandomGenerator;

/** The strategy of each level. */
public final class KiloDeMerdeBots {

    private KiloDeMerdeBots() {
    }

    /** @param random the random source of the {@code easy} level (seeded in tests) */
    public static KiloDeMerdeBotStrategy forLevel(KiloDeMerdeBotLevel level, RandomGenerator random) {
        return switch (level) {
            case EASY -> new RandomKiloDeMerdeBot(random);
            case MEDIUM -> new GreedyKiloDeMerdeBot();
            case HARD -> new MemoryKiloDeMerdeBot();
        };
    }
}
