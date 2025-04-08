package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class CanHitSameCardAsBelowTest {

    private CanHitSameCardAsBelow rule;

    @BeforeEach
    public void init() {
        rule = new CanHitSameCardAsBelow();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                    .withCardsWithRanks(FrenchRank.FIVE)
                    .build();

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenTwoDifferentCards_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.FIVE, FrenchRank.FOUR)
                .build();

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenTwoSameCards_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.EIGHT, FrenchRank.EIGHT)
                .build();

        assertThat(rule.applies(pile), is(true));
    }

}