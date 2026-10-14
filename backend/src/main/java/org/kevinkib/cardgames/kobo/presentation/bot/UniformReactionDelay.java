package org.kevinkib.cardgames.kobo.presentation.bot;

import org.kevinkib.cardgames.kobo.domain.bot.KoboBotTuning;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * A reaction time drawn uniformly between the tuning's bounds, both multiplied by a scale (1 in production,
 * 0 under the {@code test} profile, where it means "no delay").
 */
public class UniformReactionDelay implements ReactionDelay {

    private final double scale;
    private final RandomGenerator random;

    public UniformReactionDelay(double scale, RandomGenerator random) {
        if (scale < 0) {
            throw new IllegalArgumentException("Invalid reaction scale " + scale);
        }
        this.scale = scale;
        this.random = random;
    }

    @Override
    public Duration next(KoboBotTuning tuning) {
        long min = Math.round(tuning.reactionMinMs() * scale);
        long max = Math.round(tuning.reactionMaxMs() * scale);
        if (max <= min) {
            return Duration.ofMillis(min);
        }
        return Duration.ofMillis(min + random.nextLong(max - min + 1));
    }
}
