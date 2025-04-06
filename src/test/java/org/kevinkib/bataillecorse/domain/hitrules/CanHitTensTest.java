package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.cards.testhelpers.PileFixtures.createEmptyPile;
import static org.kevinkib.cards.testhelpers.PileFixtures.createPileWithRank;

class CanHitTensTest {

    private CanHitTens canHitTens;

    @BeforeEach
    public void init() {
        canHitTens = new CanHitTens();
    }

    @Test
    public void givenEmptyPile_thenDoesNotApply() {
        Pile pile = createEmptyPile();

        assertThat(canHitTens.applies(pile), is(false));
    }

    @Test
    public void givenTen_thenApplies() {
        Pile pile = createPileWithRank(FrenchRank.TEN);

        assertThat(canHitTens.applies(pile), is(true));
    }

    @Test
    public void givenNotTen_thenDoesNotApply() {
        FrenchRank notTen = FrenchRank.ACE;
        Pile pile = createPileWithRank(notTen);

        assertThat(canHitTens.applies(pile), is(false));
    }

}