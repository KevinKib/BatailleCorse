package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiloDeckTest {

    @ParameterizedTest
    @CsvSource({
            "3,3,12,40",
            "4,4,16,36",
            "5,5,20,32",
            "6,6,24,28",
            "7,7,28,24",
            "8,8,32,20",
            "9,9,36,16",
            "10,10,40,12"})
    void handDeckAndPileSizesFollowTheTable(int players, int ranks, int handDeck, int pile) {
        assertEquals(ranks, KiloDeck.dealtRanks(players).size());
        assertEquals(handDeck, KiloDeck.handDeck(players).size());
        assertEquals(pile, KiloDeck.kiloPile(players).size());
    }

    @Test
    void threePlayersDealJacksQueensKings() {
        assertEquals(List.of(FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING), KiloDeck.dealtRanks(3));
    }

    @Test
    void eachExtraPlayerAddsTheNextLowRank() {
        assertEquals(List.of(FrenchRank.TWO, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING),
                KiloDeck.dealtRanks(4));
        assertEquals(List.of(FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX,
                FrenchRank.EIGHT, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING), KiloDeck.dealtRanks(9));
    }

    @Test
    void nineAndTenPlayersSkipTheSeven() {
        assertFalse(KiloDeck.dealtRanks(9).contains(FrenchRank.SEVEN));
        assertTrue(KiloDeck.dealtRanks(10).contains(FrenchRank.NINE));
        assertFalse(KiloDeck.dealtRanks(10).contains(FrenchRank.SEVEN));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10})
    void sevensAcesAndTensAreNeverDealt(int players) {
        for (Card card : KiloDeck.handDeck(players)) {
            assertTrue(card.getRank() != FrenchRank.SEVEN && card.getRank() != FrenchRank.ACE
                    && card.getRank() != FrenchRank.TEN, "dealt " + card);
        }
        assertTrue(KiloDeck.kiloPile(players).stream().anyMatch(c -> c.getRank() == FrenchRank.SEVEN));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10})
    void handDeckAndPileAreDisjointAndMakeTheFiftyTwoCards(int players) {
        Set<Card> all = new HashSet<>(KiloDeck.handDeck(players));
        all.addAll(KiloDeck.kiloPile(players));
        assertEquals(52, all.size());
        assertEquals(52, KiloDeck.handDeck(players).size() + KiloDeck.kiloPile(players).size());
        assertTrue(all.stream().noneMatch(c -> c.getRank() == FrenchRank.JOKER));
    }

    @Test
    void anUnsupportedPlayerCountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> KiloDeck.dealtRanks(2));
        assertThrows(IllegalArgumentException.class, () -> KiloDeck.dealtRanks(11));
    }
}
