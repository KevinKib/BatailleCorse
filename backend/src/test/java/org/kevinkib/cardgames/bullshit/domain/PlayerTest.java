package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PlayerTest {

    @Test
    void givenChosenCards_whenDiscarded_thenRemovedFromHand() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE, FrenchRank.ACE, FrenchRank.KING).build();
        List<Card> toDiscard = player.getCards().subList(0, 2);

        player.discard(toDiscard);

        assertThat(player.handSize(), is(1));
        assertThat(player.hasAnyCards(), is(true));
    }

    @Test
    void givenLastCardsDiscarded_thenHandEmpty() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();

        player.discard(player.getCards());

        assertThat(player.hasAnyCards(), is(false));
    }

    @Test
    void givenCardsAddedFromPile_thenHandGrows() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();
        Player other = PlayerBuilder.aPlayer().withRanks(FrenchRank.TWO, FrenchRank.THREE).build();

        player.addCards(other.getCards());

        assertThat(player.handSize(), is(3));
    }

    @Test
    void givenCardNotHeld_whenPossessesAll_thenFalse() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();
        Player other = PlayerBuilder.aPlayer().withRanks(FrenchRank.TWO).build();

        assertThat(player.possessesAll(other.getCards()), is(false));
    }
}
