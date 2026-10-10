package org.kevinkib.cardgames.bullshit.domain.bot;

/**
 * Estimates how plausible a claim is from card counts alone: the probability that the claimant held
 * at least as many matching cards as they claimed, a hypergeometric tail.
 */
public final class PlausibilityEstimator {

    private static final double[] LOG_FACTORIAL = new double[128];

    static {
        for (int i = 1; i < LOG_FACTORIAL.length; i++) {
            LOG_FACTORIAL[i] = LOG_FACTORIAL[i - 1] + Math.log(i);
        }
    }

    private PlausibilityEstimator() {
    }

    /**
     * @param claimedCount             number of cards the claimant said they played
     * @param matchingUnseen           cards matching the target that the observer cannot see (known ones included)
     * @param knownMatchesWithClaimant matching cards the observer knows the claimant holds (revealed earlier)
     * @param unseenPopulation         cards the observer cannot see (known ones included)
     * @param claimantHandBefore       the claimant's hand size before playing (known cards included)
     * @return probability in [0, 1] that the claimant really held {@code claimedCount} matching cards
     */
    public static double probabilityTrue(int claimedCount, int matchingUnseen, int knownMatchesWithClaimant,
                                         int unseenPopulation, int claimantHandBefore) {
        if (claimedCount < 0 || matchingUnseen < 0 || knownMatchesWithClaimant < 0
                || unseenPopulation < 0 || claimantHandBefore < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
        if (matchingUnseen > unseenPopulation || claimantHandBefore > unseenPopulation
                || knownMatchesWithClaimant > matchingUnseen || knownMatchesWithClaimant > claimantHandBefore) {
            throw new IllegalArgumentException("Inconsistent counts");
        }

        int need = claimedCount - knownMatchesWithClaimant;
        if (need <= 0) {
            return 1.0;
        }
        // The known cards are certain; the rest of the claimant's hand is drawn from the unknown cards.
        int population = unseenPopulation - knownMatchesWithClaimant;
        int successes = matchingUnseen - knownMatchesWithClaimant;
        int draws = claimantHandBefore - knownMatchesWithClaimant;
        if (need > successes || need > draws) {
            return 0.0;
        }

        double tail = 0.0;
        for (int x = need; x <= Math.min(successes, draws); x++) {
            tail += Math.exp(logChoose(successes, x)
                    + logChoose(population - successes, draws - x)
                    - logChoose(population, draws));
        }
        return Math.min(1.0, tail);
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        }
        return LOG_FACTORIAL[n] - LOG_FACTORIAL[k] - LOG_FACTORIAL[n - k];
    }
}
