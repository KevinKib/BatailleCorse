package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.cards.testhelpers.PileFixtures.createPileWithRank;

class CanHitSameCardAsBelowTest {

    private CanHitSameCardAsBelow rule;

    @BeforeEach
    public void init() {
        rule = new CanHitSameCardAsBelow();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        Pile pile = createPileWithRank(FrenchRank.FIVE);

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenTwoDifferentCards_thenDoesNotApply() {
        Pile pile = createPileWithRank(FrenchRank.FIVE, FrenchRank.FOUR);

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenTwoSameCards_thenApplies() {
        Pile pile = createPileWithRank(FrenchRank.EIGHT, FrenchRank.EIGHT);

        assertThat(rule.applies(pile), is(true));
    }

}