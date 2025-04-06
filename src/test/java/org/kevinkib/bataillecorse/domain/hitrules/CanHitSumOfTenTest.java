package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.kevinkib.cards.testhelpers.PileFixtures.createPileWithNumberOfCards;
import static org.kevinkib.cards.testhelpers.PileFixtures.createPileWithRank;

class CanHitSumOfTenTest {

    private CanHitSumOfTen rule;

    @BeforeEach
    public void init() {
        rule = new CanHitSumOfTen();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        Pile pile = createPileWithNumberOfCards(1);

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenSumOfTen_thenApplies() {
        Pile pile = createPileWithRank(FrenchRank.FOUR, FrenchRank.SIX);

        assertThat(rule.applies(pile), is(true));
    }

    @Test
    public void givenNotSumOfTen_thenDoesNotApply() {
        Pile pile = createPileWithRank(FrenchRank.FOUR, FrenchRank.FIVE);

        assertThat(rule.applies(pile), is(false));
    }

}