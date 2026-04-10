package org.kevinkib.bataillecorse.domain.slaprules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class CanSlapSumOfTenTest {

    private CanSlapSumOfTen rule;

    @BeforeEach
    public void init() {
        rule = new CanSlapSumOfTen();
    }

    @Test
    public void givenNotEnoughCards_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withNumberOfCards(1)
                .build();

        assertThat(rule.applies(pile), is(false));
    }

    @Test
    public void givenSumOfTen_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.FOUR, FrenchRank.SIX)
                .build();

        assertThat(rule.applies(pile), is(true));
    }

    @Test
    public void givenNotSumOfTen_thenDoesNotApply() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.FOUR, FrenchRank.FIVE)
                .build();

        assertThat(rule.applies(pile), is(false));
    }

}