package org.kevinkib.bataillecorse.domain.slaprules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.bataillecorse.domain.CentralPileFixtures.createEmptyCentralPile;

class CanSlapTensTest {

    private CanSlapTens canSlapTens;

    @BeforeEach
    public void init() {
        canSlapTens = new CanSlapTens();
    }

    @Test
    public void givenEmptyPile_thenDoesNotApply() {
        CentralPile pile = createEmptyCentralPile();

        assertThat(canSlapTens.applies(pile), is(false));
    }

    @Test
    public void givenTen_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.TEN)
                .build();

        assertThat(canSlapTens.applies(pile), is(true));
    }

    @Test
    public void givenNotTen_thenDoesNotApply() {
        FrenchRank notTen = FrenchRank.ACE;
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(notTen)
                .build();

        assertThat(canSlapTens.applies(pile), is(false));
    }

}