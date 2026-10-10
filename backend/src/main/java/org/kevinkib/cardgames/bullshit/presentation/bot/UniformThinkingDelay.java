package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;
import java.util.random.RandomGenerator;

/** A thinking delay drawn uniformly between two bounds (both zero means "no delay"). */
public class UniformThinkingDelay implements ThinkingDelay {

    private final long minMillis;
    private final long maxMillis;
    private final RandomGenerator random;

    public UniformThinkingDelay(long minMillis, long maxMillis, RandomGenerator random) {
        if (minMillis < 0 || maxMillis < minMillis) {
            throw new IllegalArgumentException("Invalid delay bounds " + minMillis + ".." + maxMillis);
        }
        this.minMillis = minMillis;
        this.maxMillis = maxMillis;
        this.random = random;
    }

    @Override
    public Duration next() {
        if (minMillis == maxMillis) {
            return Duration.ofMillis(minMillis);
        }
        return Duration.ofMillis(minMillis + random.nextLong(maxMillis - minMillis + 1));
    }
}
