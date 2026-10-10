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

    // ---- probabilityGenuine: a claim read against how a player who plays honestly when able behaves ----

    private static final PlausibilityEstimator.ClaimPrior LIES_ONE_CARD_WHEN_EMPTY_HANDED =
            new PlausibilityEstimator.ClaimPrior(0.0, 1.0, 4);

    @Test
    void givenSingleCardClaim_thenOneMatchBeatsNoMatchByTheirRelativeLikelihood() {
        // unseen 40, 3 matches, hand 10: P(1 match) / P(0 match) = 3 * 10 / 28, so genuine = 30 / 58
        double p = PlausibilityEstimator.probabilityGenuine(1, 3, 0, 40, 10, LIES_ONE_CARD_WHEN_EMPTY_HANDED);

        assertThat(p, closeTo(30.0 / 58.0, EPS));
    }

    @Test
    void givenBigHandAndSingleCardClaim_thenLyingIsUnlikelyBecauseTheClaimantUsuallyHasAMatch() {
        double bigHand = PlausibilityEstimator.probabilityGenuine(1, 4, 0, 40, 26, LIES_ONE_CARD_WHEN_EMPTY_HANDED);
        double smallHand = PlausibilityEstimator.probabilityGenuine(1, 4, 0, 40, 3, LIES_ONE_CARD_WHEN_EMPTY_HANDED);

        assertThat(bigHand, greaterThan(0.9));
        assertThat(smallHand, lessThan(0.5));
    }

    @Test
    void givenNoMatchCanBeHeld_thenTheClaimIsALie() {
        assertThat(PlausibilityEstimator.probabilityGenuine(1, 0, 0, 40, 10, LIES_ONE_CARD_WHEN_EMPTY_HANDED), is(0.0));
    }

    @Test
    void givenAMatchIsKnownToBeHeld_thenAnHonestSingleCardClaimIsCertain() {
        assertThat(PlausibilityEstimator.probabilityGenuine(1, 3, 1, 40, 10, LIES_ONE_CARD_WHEN_EMPTY_HANDED), is(1.0));
    }

    @Test
    void givenBluffsAddingAnOffCardToHonestPlays_thenAMultiCardClaimIsLessTrustworthy() {
        double withoutBluffs = PlausibilityEstimator.probabilityGenuine(2, 4, 0, 40, 10,
                new PlausibilityEstimator.ClaimPrior(0.0, 0.25, 4));
        double withBluffs = PlausibilityEstimator.probabilityGenuine(2, 4, 0, 40, 10,
                new PlausibilityEstimator.ClaimPrior(0.3, 0.25, 4));

        assertThat(withBluffs, lessThan(withoutBluffs));
    }

    @Test
    void givenAClaimOfTheMaximumPlay_thenAnyHandOfThatManyMatchesOrMoreIsGenuine() {
        // With four cards allowed and four matches unseen, only holding all four makes the claim of four genuine.
        double p = PlausibilityEstimator.probabilityGenuine(4, 4, 0, 10, 10, new PlausibilityEstimator.ClaimPrior(0.0, 0.05, 4));

        assertThat(p, closeTo(1.0, EPS));
    }

    @Test
    void givenNeitherAnHonestPlayNorALieCanExplainTheClaim_thenFallsBackToTheCountingEstimate() {
        // claims 4 with only 3 matches unseen and no lie of that size: the plain estimate says impossible
        double p = PlausibilityEstimator.probabilityGenuine(4, 3, 0, 40, 10, new PlausibilityEstimator.ClaimPrior(0.0, 0.0, 4));

        assertThat(p, is(0.0));
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
