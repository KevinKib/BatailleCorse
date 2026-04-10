package org.kevinkib.bataillecorse.core.domain.slaprules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.CentralPile;
import org.kevinkib.bataillecorse.core.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.testhelpers.CardBuilder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class CanSlapSameCardAsBelowTest {

    private CanSlapSameCardAsBelow rule;

    @BeforeEach
    public void init() {
        rule = new CanSlapSameCardAsBelow();
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

    @Test
    public void givenCardsWithSameRanksAndDifferentSuits_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCards(
                        CardBuilder.aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.CLUB).build(),
                        CardBuilder.aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.DIAMOND).build()
                ).build();

        assertThat(rule.applies(pile), is(true));
    }

}