package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class DiscardPileTest {

    private final Card aceSpade = aCard().withRank(FrenchRank.ACE).withSuit(FrenchSuit.SPADE).build();
    private final Card twoHeart = aCard().withRank(FrenchRank.TWO).withSuit(FrenchSuit.HEART).build();

    @Test
    void givenNewPile_thenEmpty() {
        DiscardPile pile = new DiscardPile();
        assertThat(pile.isEmpty(), is(true));
        assertThat(pile.size(), is(0));
    }

    @Test
    void whenCardsAdded_thenSizeGrows() {
        DiscardPile pile = new DiscardPile();
        pile.add(List.of(aceSpade));
        pile.add(List.of(twoHeart));
        assertThat(pile.size(), is(2));
        assertThat(pile.isEmpty(), is(false));
    }

    @Test
    void whenTakeAll_thenReturnsEverythingAndEmpties() {
        DiscardPile pile = new DiscardPile();
        pile.add(List.of(aceSpade, twoHeart));

        List<Card> taken = pile.takeAll();

        assertThat(taken, contains(aceSpade, twoHeart));
        assertThat(pile.isEmpty(), is(true));
    }
}
