package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.Color;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class AlternatingColorClaimModeTest {

    private final AlternatingColorClaimMode mode = new AlternatingColorClaimMode();

    @Test
    void givenNewGame_whenInitial_thenRed() {
        assertThat(mode.initial(), is(new ColorTarget(Color.RED)));
    }

    @Test
    void givenRed_whenNext_thenBlack() {
        assertThat(mode.next(new ColorTarget(Color.RED)), is(new ColorTarget(Color.BLACK)));
    }

    @Test
    void givenBlack_whenNext_thenWrapsToRed() {
        assertThat(mode.next(new ColorTarget(Color.BLACK)), is(new ColorTarget(Color.RED)));
    }

    @Test
    void givenAllCardsOfClaimedColour_whenMatches_thenTrue() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),     // RED
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.DIAMOND).build());    // RED
        assertThat(mode.matches(cards, new ColorTarget(Color.RED)), is(true));
    }

    @Test
    void givenAnyOffColourCard_whenMatches_thenFalse() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),      // RED
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.SPADE).build());       // BLACK
        assertThat(mode.matches(cards, new ColorTarget(Color.RED)), is(false));
    }
}
