package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class CyclingSuitClaimModeTest {

    private final CyclingSuitClaimMode mode = new CyclingSuitClaimMode();

    @Test
    void givenNewGame_whenInitial_thenHeart() {
        assertThat(mode.initial(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenHeart_whenNext_thenDiamond() {
        assertThat(mode.next(new SuitTarget(FrenchSuit.HEART)), is(new SuitTarget(FrenchSuit.DIAMOND)));
    }

    @Test
    void givenSpade_whenNext_thenWrapsToHeart() {
        assertThat(mode.next(new SuitTarget(FrenchSuit.SPADE)), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenAllCardsOfClaimedSuit_whenMatches_thenTrue() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build(),
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new SuitTarget(FrenchSuit.CLUB)), is(true));
    }

    @Test
    void givenAnyOffSuitCard_whenMatches_thenFalse() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build(),
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.SPADE).build());
        assertThat(mode.matches(cards, new SuitTarget(FrenchSuit.CLUB)), is(false));
    }
}
