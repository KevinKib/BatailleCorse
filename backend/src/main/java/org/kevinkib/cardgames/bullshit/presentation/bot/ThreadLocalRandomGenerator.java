package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Production randomness: safe to share between threads because every draw uses the caller's own generator. */
public final class ThreadLocalRandomGenerator implements RandomGenerator {

    @Override
    public long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    @Override
    public long nextLong(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }
}
