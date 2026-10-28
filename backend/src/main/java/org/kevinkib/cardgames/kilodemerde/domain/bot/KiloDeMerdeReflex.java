package org.kevinkib.cardgames.kilodemerde.domain.bot;

import java.util.random.RandomGenerator;

/**
 * How long a Kilo de merde bot waits before it acts (spec section 11): pure, the random source is injected so a
 * seeded generator gives the same delays.
 */
public final class KiloDeMerdeReflex {

    private final KiloDeMerdeBotTuning tuning;

    public KiloDeMerdeReflex(KiloDeMerdeBotTuning tuning) {
        this.tuning = tuning;
    }

    /** The delay before the bot passes a card, the same for every level. */
    public long passDelayMs(RandomGenerator random) {
        return sample(tuning.pass(), random);
    }

    /** The delay before the bot slaps a four of a kind it holds. */
    public long ownSetDelayMs(KiloDeMerdeBotLevel level, RandomGenerator random) {
        return sample(tuning.forLevel(level).ownSet(), random);
    }

    /** The delay before the bot follows a race another seat opened. */
    public long followDelayMs(KiloDeMerdeBotLevel level, RandomGenerator random) {
        return sample(tuning.forLevel(level).follow(), random);
    }

    private static long sample(KiloDeMerdeBotTuning.Range range, RandomGenerator random) {
        if (range.minMs() == range.maxMs()) {
            return range.minMs();
        }
        return range.minMs() + random.nextLong(range.maxMs() - range.minMs() + 1);
    }
}
