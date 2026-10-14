package org.kevinkib.cardgames.kobo.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotLevel;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotTuning;

import java.time.Duration;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class UniformReactionDelayTest {

    private static final KoboBotTuning MEDIUM = KoboBotLevel.MEDIUM.tuning();

    @Test
    void theDelayIsDrawnWithinTheTuningBounds() {
        UniformReactionDelay delay = new UniformReactionDelay(1.0, new Random(5));

        for (int i = 0; i < 500; i++) {
            long millis = delay.next(MEDIUM).toMillis();
            assertThat(millis, greaterThanOrEqualTo((long) MEDIUM.reactionMinMs()));
            assertThat(millis, lessThanOrEqualTo((long) MEDIUM.reactionMaxMs()));
        }
    }

    @Test
    void theScaleMultipliesTheBounds() {
        UniformReactionDelay half = new UniformReactionDelay(0.5, new Random(5));

        for (int i = 0; i < 500; i++) {
            long millis = half.next(MEDIUM).toMillis();
            assertThat(millis, greaterThanOrEqualTo(MEDIUM.reactionMinMs() / 2L));
            assertThat(millis, lessThanOrEqualTo(MEDIUM.reactionMaxMs() / 2L));
        }
    }

    @Test
    void aScaleOfZeroGivesNoDelay() {
        assertThat(new UniformReactionDelay(0, new Random(5)).next(MEDIUM), is(Duration.ZERO));
    }

    @Test
    void aNegativeScaleIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new UniformReactionDelay(-1, new Random(5)));
    }

    @Test
    void aFasterLevelReactsSooner() {
        UniformReactionDelay delay = new UniformReactionDelay(1.0, new Random(9));

        long easyMin = KoboBotLevel.EASY.tuning().reactionMinMs();
        long hardMax = KoboBotLevel.HARD.tuning().reactionMaxMs();
        for (int i = 0; i < 200; i++) {
            assertThat(delay.next(KoboBotLevel.EASY.tuning()).toMillis(), greaterThanOrEqualTo(easyMin));
            assertThat(delay.next(KoboBotLevel.HARD.tuning()).toMillis(), lessThanOrEqualTo(hardMax));
        }
    }
}
