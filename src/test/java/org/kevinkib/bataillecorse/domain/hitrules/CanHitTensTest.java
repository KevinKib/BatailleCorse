package org.kevinkib.bataillecorse.domain.hitrules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.bataillecorse.domain.CentralPileBuilder;
import org.kevinkib.cards.domain.french.FrenchRank;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.bataillecorse.domain.CentralPileFixtures.createEmptyCentralPile;

class CanHitTensTest {

    private CanHitTens canHitTens;

    @BeforeEach
    public void init() {
        canHitTens = new CanHitTens();
    }

    @Test
    public void givenEmptyPile_thenDoesNotApply() {
        CentralPile pile = createEmptyCentralPile();

        assertThat(canHitTens.applies(pile), is(false));
    }

    @Test
    public void givenTen_thenApplies() {
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(FrenchRank.TEN)
                .build();

        assertThat(canHitTens.applies(pile), is(true));
    }

    @Test
    public void givenNotTen_thenDoesNotApply() {
        FrenchRank notTen = FrenchRank.ACE;
        CentralPile pile = CentralPileBuilder.aCentralPile()
                .withCardsWithRanks(notTen)
                .build();

        assertThat(canHitTens.applies(pile), is(false));
    }

}