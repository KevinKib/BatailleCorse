package org.kevinkib.cardgames.bullshit.domain.bot;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlausibilityEstimatorTest {

    private static final double EPS = 1e-9;

    @Test
    void givenClaimLargerThanTheUnseenMatches_thenImpossible() {
        assertThat(PlausibilityEstimator.probabilityTrue(3, 2, 0, 40, 10), is(0.0));
    }

    @Test
    void givenKnownMatchesAlreadyCoverTheClaim_thenCertain() {
        assertThat(PlausibilityEstimator.probabilityTrue(2, 3, 2, 40, 10), is(1.0));
        assertThat(PlausibilityEstimator.probabilityTrue(1, 3, 2, 40, 10), is(1.0));
    }

    @Test
    void givenSingleCardClaim_thenOneMinusProbabilityOfMissingEveryMatch() {
        // population 40, 3 matches, claimant held 10: 1 - C(37,10) / C(40,10) = 1 - (30*29*28)/(40*39*38)
        double expected = 1 - (30.0 * 29 * 28) / (40.0 * 39 * 38);

        assertThat(PlausibilityEstimator.probabilityTrue(1, 3, 0, 40, 10), closeTo(expected, EPS));
    }

    @Test
    void givenKnownMatchWithClaimant_thenOnlyTheRemainingNeedIsEstimatedOverTheUnknownCards() {
        // claims 2, one match known with the claimant: needs 1 of the 2 other matches among the claimant's
        // 9 other cards drawn from 39 unknown cards: 1 - C(37,9) / C(39,9) = 1 - (30*29)/(39*38)
        double expected = 1 - (30.0 * 29) / (39.0 * 38);

        assertThat(PlausibilityEstimator.probabilityTrue(2, 3, 1, 40, 10), closeTo(expected, EPS));
    }

    @Test
    void givenLargerClaims_thenProbabilityNeverIncreases() {
        double previous = 1.0;
        for (int claimed = 1; claimed <= 4; claimed++) {
            double p = PlausibilityEstimator.probabilityTrue(claimed, 4, 0, 40, 12);
            assertThat(p, lessThan(previous + EPS));
            previous = p;
        }
        assertThat(PlausibilityEstimator.probabilityTrue(1, 4, 0, 40, 12), greaterThan(0.0));
    }

    @Test
    void givenClaimantHoldingEverythingUnseen_thenCertain() {
        assertThat(PlausibilityEstimator.probabilityTrue(4, 4, 0, 10, 10), closeTo(1.0, EPS));
    }

    @Test
    void givenFullDeckSizedInputs_thenNoOverflowAndAProbability() {
        double p = PlausibilityEstimator.probabilityTrue(2, 4, 0, 52, 26);

        assertThat(p > 0 && p < 1, is(true));
    }

    @Test
    void givenInconsistentInputs_thenRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(-1, 3, 0, 40, 10));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, -3, 0, 40, 10));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 3, -1, 40, 10));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 3, 0, 40, -1));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 3, 4, 40, 10));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 41, 0, 40, 10));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 3, 0, 40, 41));
        assertThrows(IllegalArgumentException.class, () -> PlausibilityEstimator.probabilityTrue(1, 3, 3, 40, 2));
    }
}
