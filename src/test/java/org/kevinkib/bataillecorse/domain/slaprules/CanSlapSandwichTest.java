package org.kevinkib.bataillecorse.domain.slaprules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.domain.french.FrenchSuit;
import org.kevinkib.cards.testhelpers.CardBuilder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;


class CanSlapSandwichTest {

    private CanSlapSandwich rule;

    @BeforeEach
    public void init() {
        rule = new CanSlapSandwich();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withNumberOfCards(2)
                .build();

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenSandwich_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.EIGHT, FrenchRank.FOUR, FrenchRank.EIGHT)
                .build();

        assertThat(rule.applies(pile), is(true));
    }

    @Test
    public void givenCardsWithSameRanksAndDifferentSuits_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCards(
                        CardBuilder.aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.CLUB).build(),
                        CardBuilder.aCard().withRank(FrenchRank.FOUR).build(),
                        CardBuilder.aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.DIAMOND).build()
                ).build();

        assertThat(rule.applies(pile), is(true));
    }

    @Test
    public void givenNotSandwich_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.EIGHT, FrenchRank.FOUR, FrenchRank.SIX)
                .build();

        assertThat(rule.applies(pile), is(false));
    }

}