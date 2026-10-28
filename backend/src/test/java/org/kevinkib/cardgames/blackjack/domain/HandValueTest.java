package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandValueTest {

    private static Card card(FrenchRank rank) {
        return new Card(rank, FrenchSuit.SPADE);
    }

    private static HandValue of(boolean fromSplit, FrenchRank... ranks) {
        return HandValue.of(Arrays.stream(ranks).map(HandValueTest::card).toList(), fromSplit);
    }

    @ParameterizedTest
    @CsvSource({"TWO,2", "THREE,3", "FOUR,4", "FIVE,5", "SIX,6", "SEVEN,7", "EIGHT,8", "NINE,9", "TEN,10",
            "JACK,10", "QUEEN,10", "KING,10"})
    void everyNonAceRankCountsItsBlackjackValue(FrenchRank rank, int expected) {
        HandValue value = of(false, rank);
        assertEquals(expected, value.total());
        assertFalse(value.soft());
    }

    @Test
    void aSingleAceIsElevenAndSoft() {
        HandValue value = of(false, FrenchRank.ACE);
        assertEquals(11, value.total());
        assertTrue(value.soft());
    }

    @Test
    void aceAndSixIsSeventeenSoft() {
        HandValue value = of(false, FrenchRank.ACE, FrenchRank.SIX);
        assertEquals(17, value.total());
        assertTrue(value.soft());
    }

    @Test
    void aceAceNineIsTwentyOneSoft() {
        HandValue value = of(false, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.NINE);
        assertEquals(21, value.total());
        assertTrue(value.soft());
        assertFalse(value.blackjack());
    }

    @Test
    void aceSixTenIsSeventeenHard() {
        HandValue value = of(false, FrenchRank.ACE, FrenchRank.SIX, FrenchRank.TEN);
        assertEquals(17, value.total());
        assertFalse(value.soft());
    }

    @Test
    void severalAcesCountOneExceptPossiblyOne() {
        HandValue value = of(false, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE);
        assertEquals(14, value.total());
        assertTrue(value.soft());
    }

    @Test
    void aceKingIsABlackjack() {
        HandValue value = of(false, FrenchRank.ACE, FrenchRank.KING);
        assertEquals(21, value.total());
        assertTrue(value.soft());
        assertTrue(value.blackjack());
        assertFalse(value.bust());
    }

    @Test
    void aceKingAfterASplitIsPlainTwentyOne() {
        HandValue value = of(true, FrenchRank.ACE, FrenchRank.KING);
        assertEquals(21, value.total());
        assertFalse(value.blackjack());
    }

    @Test
    void threeCardsTwentyOneIsNotABlackjack() {
        HandValue value = of(false, FrenchRank.SEVEN, FrenchRank.SEVEN, FrenchRank.SEVEN);
        assertEquals(21, value.total());
        assertFalse(value.blackjack());
    }

    @Test
    void overTwentyOneIsABust() {
        HandValue value = of(false, FrenchRank.KING, FrenchRank.SIX, FrenchRank.NINE);
        assertEquals(25, value.total());
        assertTrue(value.bust());
        assertFalse(value.soft());
    }

    @Test
    void twentyOneIsNotABust() {
        assertFalse(of(false, FrenchRank.KING, FrenchRank.FIVE, FrenchRank.SIX).bust());
    }

    @Test
    void anEmptyHandIsZero() {
        HandValue value = HandValue.of(List.of(), false);
        assertEquals(0, value.total());
        assertFalse(value.soft());
        assertFalse(value.blackjack());
    }

    @Test
    void aJokerIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> of(false, FrenchRank.JOKER));
    }
}
