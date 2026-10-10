package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniformThinkingDelayTest {

    @Test
    void givenBounds_thenEveryDelayStaysInsideAndBothEndsAreReachable() {
        UniformThinkingDelay delay = new UniformThinkingDelay(1000, 2000, new Random(7));
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (int i = 0; i < 5000; i++) {
            long millis = delay.next().toMillis();
            min = Math.min(min, millis);
            max = Math.max(max, millis);
        }

        assertThat(min >= 1000, is(true));
        assertThat(max <= 2000, is(true));
        assertThat(max - min, greaterThan(900L));
    }

    @Test
    void givenZeroBounds_thenNoDelay() {
        assertThat(new UniformThinkingDelay(0, 0, new Random(1)).next(), is(Duration.ZERO));
    }

    @Test
    void givenInvertedBounds_thenRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UniformThinkingDelay(2000, 1000, new Random(1)));
    }
}
