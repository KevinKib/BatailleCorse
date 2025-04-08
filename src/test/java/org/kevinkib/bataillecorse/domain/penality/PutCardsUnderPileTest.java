package org.kevinkib.bataillecorse.domain.penality;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.*;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.kevinkib.bataillecorse.domain.penality.PutCardsUnderPileBuilder.aPutCardsUnderPilePenality;

class PutCardsUnderPileTest {

    @Test
    public void givenPlayerWithEnoughCards_shouldApply() {
        Card specificCard = CardBuilder.aCard().withRank(FrenchRank.ACE).build();
        Card defaultCard = CardBuilder.aCard().build();

        Penality penality = aPutCardsUnderPilePenality()
                .withGivenCards(2)
                .build();

        CentralPile pile = CentralPileFixtures.createCentralPileWithCardAndState(specificCard, CentralPileState.NEUTRAL);
        Player player = PlayerBuilder.aPlayer()
                .withHand(
                        HandBuilder.aHand().withCards(Arrays.asList(
                            defaultCard,
                            defaultCard
                        )).build()
                ).build();

        penality.apply(player, pile);

        assertThat(player.getHandSize(), is(0));
        assertThat(pile.getSize(), is(3));
        assertThat(pile.getCards(), is(Arrays.asList(specificCard, defaultCard, defaultCard)));
    }

    @Test
    public void givenPlayerWithNotEnoughCards_shouldGiveEveryCard() {
        Card defaultCard = CardBuilder.aCard().build();

        Penality penality = aPutCardsUnderPilePenality()
                .withGivenCards(2)
                .build();

        CentralPile pile = CentralPileFixtures.createEmptyCentralPile();

        Player player = PlayerBuilder.aPlayer()
                .withHand(
                        HandBuilder.aHand().withCards(Arrays.asList(
                            defaultCard
                        )).build()
                ).build();

        penality.apply(player, pile);

        assertThat(player.getHandSize(), is(0));
        assertThat(pile.getSize(), is(1));
    }

}