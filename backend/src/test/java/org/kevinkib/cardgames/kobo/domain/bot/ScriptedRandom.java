package org.kevinkib.cardgames.kobo.domain.bot;

import java.util.random.RandomGenerator;

/** A "random" generator that returns a fixed sequence of doubles and fails loudly when it runs dry. */
final class ScriptedRandom implements RandomGenerator {

    private final double[] values;
    private int next;

    ScriptedRandom(double... values) {
        this.values = values.clone();
    }

    @Override
    public double nextDouble() {
        if (next >= values.length) {
            throw new IllegalStateException("Random script exhausted after " + values.length + " draws");
        }
        return values[next++];
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException("Strategies must draw doubles only");
    }

    int drawn() {
        return next;
    }
}
