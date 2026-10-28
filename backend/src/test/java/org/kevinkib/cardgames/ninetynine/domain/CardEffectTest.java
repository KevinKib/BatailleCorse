package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardEffectTest {

    private static Card card(FrenchRank rank) {
        return new Card(rank, FrenchSuit.SPADE);
    }

    @ParameterizedTest
    @CsvSource({
            "TWO,10,12", "THREE,10,13", "FIVE,10,15", "SIX,10,16", "SEVEN,10,17", "EIGHT,10,18",
            "FOUR,37,37", "NINE,37,37", "JACK,10,20", "QUEEN,10,20", "KING,10,99", "KING,99,99",
            "TEN,25,15", "TEN,10,0", "TEN,6,0", "TEN,0,0"})
    void appliesTheOwnersTable(FrenchRank rank, int total, int expected) {
        assertEquals(expected, CardEffect.apply(total, card(rank), null));
    }

    @Test
    void aceAddsOneOrEleven() {
        assertEquals(51, CardEffect.apply(50, card(FrenchRank.ACE), 1));
        assertEquals(61, CardEffect.apply(50, card(FrenchRank.ACE), 11));
    }

    @Test
    void aceNeedsAValueOfOneOrEleven() {
        assertThrows(IllegalArgumentException.class, () -> CardEffect.apply(50, card(FrenchRank.ACE), null));
        assertThrows(IllegalArgumentException.class, () -> CardEffect.apply(50, card(FrenchRank.ACE), 5));
    }

    @Test
    void aceValueIsIgnoredForOtherCards() {
        assertEquals(12, CardEffect.apply(10, card(FrenchRank.TWO), 11));
    }

    @Test
    void onlyTheFourReverses() {
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER) {
                continue;
            }
            assertEquals(rank == FrenchRank.FOUR, CardEffect.reverses(card(rank)), rank.name());
        }
    }

    @Test
    void aceValuesOfferElevenOnlyWhenItStaysAtNinetyNineOrBelow() {
        assertEquals(List.of(1, 11), CardEffect.aceValues(0));
        assertEquals(List.of(1, 11), CardEffect.aceValues(88));
        assertEquals(List.of(1), CardEffect.aceValues(89));
        assertEquals(List.of(1), CardEffect.aceValues(98));
        assertEquals(List.of(), CardEffect.aceValues(99));
    }

    @Test
    void totalOfExactlyNinetyNineIsSafe() {
        assertTrue(CardEffect.isSafe(91, card(FrenchRank.EIGHT)));
        assertFalse(CardEffect.isSafe(92, card(FrenchRank.EIGHT)));
        assertTrue(CardEffect.isSafe(98, card(FrenchRank.ACE)));
        assertFalse(CardEffect.isSafe(99, card(FrenchRank.ACE)));
    }

    @Test
    void fourNineTenAndKingAreAlwaysSafe() {
        for (FrenchRank rank : List.of(FrenchRank.FOUR, FrenchRank.NINE, FrenchRank.TEN, FrenchRank.KING)) {
            assertTrue(CardEffect.isSafe(99, card(rank)), rank.name());
        }
    }

    @Test
    void jackAndQueenGoOverFromNinety() {
        assertTrue(CardEffect.isSafe(89, card(FrenchRank.JACK)));
        assertFalse(CardEffect.isSafe(90, card(FrenchRank.QUEEN)));
    }

    @Test
    void safeCardsKeepHandOrder() {
        List<Card> hand = List.of(card(FrenchRank.SEVEN), card(FrenchRank.FOUR), card(FrenchRank.JACK));
        assertEquals(List.of(card(FrenchRank.FOUR)), CardEffect.safeCards(95, hand));
        assertEquals(hand, CardEffect.safeCards(80, hand));
        assertEquals(List.of(), CardEffect.safeCards(95, List.of(card(FrenchRank.SEVEN), card(FrenchRank.JACK))));
    }
}
