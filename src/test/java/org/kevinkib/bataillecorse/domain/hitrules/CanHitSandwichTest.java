package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.cards.testhelpers.PileFixtures.*;

class CanHitSandwichTest {

    private CanHitSandwich rule;

    @BeforeEach
    public void init() {
        rule = new CanHitSandwich();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        Pile pile = createPileWithNumberOfCards(2);

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenSandwich_thenApplies() {
        Pile pile = createPileWithRank(FrenchRank.EIGHT, FrenchRank.FOUR, FrenchRank.EIGHT);

        assertThat(rule.applies(pile), is(true));
    }

    @Test
    public void givenNotSandwich_thenDoesNotApply() {
        Pile pile = createPileWithRank(FrenchRank.EIGHT, FrenchRank.FOUR, FrenchRank.SIX);

        assertThat(rule.applies(pile), is(false));
    }

}