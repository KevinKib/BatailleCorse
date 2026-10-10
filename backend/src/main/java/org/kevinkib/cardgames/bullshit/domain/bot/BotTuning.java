package org.kevinkib.cardgames.bullshit.domain.bot;

/** Every constant of the probabilistic strategy in one place, so tests can pin them. */
public record BotTuning(
        double minCallProbability,
        double maxCallProbability,
        double honestBluffProbability,
        double lieOneCardProbability,
        double lieTwoCardsProbability,
        int maxCardsPerPlay) {

    /** The single difficulty level. */
    public static final BotTuning DEFAULT = new BotTuning(0.05, 0.95, 0.15, 0.70, 0.25, 4);

    public BotTuning {
        if (minCallProbability < 0 || maxCallProbability > 1 || minCallProbability > maxCallProbability) {
            throw new IllegalArgumentException("Invalid call probability bounds");
        }
        if (lieOneCardProbability + lieTwoCardsProbability > 1) {
            throw new IllegalArgumentException("Lie size probabilities exceed 1");
        }
        if (maxCardsPerPlay < 1) {
            throw new IllegalArgumentException("A play needs at least one card");
        }
    }
}
