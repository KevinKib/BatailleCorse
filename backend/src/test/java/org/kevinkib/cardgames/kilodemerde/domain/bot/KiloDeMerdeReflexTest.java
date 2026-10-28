package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class KiloDeMerdeReflexTest {

    private final KiloDeMerdeReflex reflex = new KiloDeMerdeReflex(KiloDeMerdeBotTuning.defaults());

    @Test
    void passDelayStaysInItsRangeForEveryLevel() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long delay = reflex.passDelayMs(random);
            assertThat(delay, greaterThanOrEqualTo(500L));
            assertThat(delay, lessThanOrEqualTo(1000L));
        }
    }

    @Test
    void ownSetAndFollowDelaysStayInTheRangesOfSpecSection7() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertBetween(reflex.ownSetDelayMs(KiloDeMerdeBotLevel.EASY, random), 1500, 2500);
            assertBetween(reflex.followDelayMs(KiloDeMerdeBotLevel.EASY, random), 1200, 2000);
            assertBetween(reflex.ownSetDelayMs(KiloDeMerdeBotLevel.MEDIUM, random), 700, 1100);
            assertBetween(reflex.followDelayMs(KiloDeMerdeBotLevel.MEDIUM, random), 700, 1100);
            assertBetween(reflex.ownSetDelayMs(KiloDeMerdeBotLevel.HARD, random), 350, 600);
            assertBetween(reflex.followDelayMs(KiloDeMerdeBotLevel.HARD, random), 450, 750);
        }
    }

    @Test
    void delaysAreDeterministicWithASeededGenerator() {
        for (KiloDeMerdeBotLevel level : KiloDeMerdeBotLevel.values()) {
            Random a = new Random(5);
            Random b = new Random(5);
            for (int i = 0; i < 20; i++) {
                assertThat(reflex.ownSetDelayMs(level, a), is(reflex.ownSetDelayMs(level, b)));
                assertThat(reflex.followDelayMs(level, a), is(reflex.followDelayMs(level, b)));
            }
        }
    }

    @Test
    void harderLevelsReactFasterOnAverage() {
        Random random = new Random(2);
        double easy = 0;
        double medium = 0;
        double hard = 0;
        for (int i = 0; i < 2000; i++) {
            easy += reflex.followDelayMs(KiloDeMerdeBotLevel.EASY, random);
            medium += reflex.followDelayMs(KiloDeMerdeBotLevel.MEDIUM, random);
            hard += reflex.followDelayMs(KiloDeMerdeBotLevel.HARD, random);
        }
        assertThat(hard < medium && medium < easy, is(true));
    }

    @Test
    void theZeroTuningNeverWaits() {
        KiloDeMerdeReflex instant = new KiloDeMerdeReflex(KiloDeMerdeBotTuning.instant());
        Random random = new Random(1);
        for (KiloDeMerdeBotLevel level : KiloDeMerdeBotLevel.values()) {
            assertThat(instant.passDelayMs(random), is(0L));
            assertThat(instant.ownSetDelayMs(level, random), is(0L));
            assertThat(instant.followDelayMs(level, random), is(0L));
        }
    }

    @Test
    void aRangeRefusesMaxBelowMin() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new KiloDeMerdeBotTuning.Range(10, 5));
    }

    private static void assertBetween(long value, long min, long max) {
        assertThat(value, greaterThanOrEqualTo(min));
        assertThat(value, lessThanOrEqualTo(max));
    }
}
