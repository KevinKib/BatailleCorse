package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.c;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;

class KiloDeMerdeRulesTest {

    @Test
    void fourCardsOfOneRankAreAFourOfAKind() {
        assertTrue(KiloDeMerdeRules.isFourOfAKind(cards("KS KH KD KC")));
    }

    @Test
    void threeOfAKindIsNotEnough() {
        assertFalse(KiloDeMerdeRules.isFourOfAKind(cards("KS KH KD QC")));
    }

    @Test
    void aHandOfAnotherSizeIsNeverAFourOfAKind() {
        assertFalse(KiloDeMerdeRules.isFourOfAKind(cards("KS KH KD")));
        assertFalse(KiloDeMerdeRules.isFourOfAKind(cards("KS KH KD KC KS")));
        assertFalse(KiloDeMerdeRules.isFourOfAKind(cards("")));
    }

    @Test
    void anyFourOfAKindLooksAtEveryHand() {
        PlayerId a = new PlayerId(0);
        PlayerId b = new PlayerId(1);
        assertTrue(KiloDeMerdeRules.anyFourOfAKind(
                Map.of(a, cards("KS KH KD QC"), b, cards("2S 2H 2D 2C")).values()));
        assertFalse(KiloDeMerdeRules.anyFourOfAKind(
                Map.of(a, cards("KS KH KD QC"), b, cards("2S 2H 2D JC")).values()));
        assertFalse(KiloDeMerdeRules.anyFourOfAKind(List.of()));
    }

    @Test
    void stallLimitIsForty() {
        assertEquals(40, KiloDeMerdeRules.STALL_LIMIT);
    }

    @ParameterizedTest
    @CsvSource({
            // worked example 9 of the spec: ranks held once are 2 and Q, the lowest first
            "'JS JH QD 2C',2C",
            // a rank held once beats a pair
            "'KS KH QD 2C',2C",
            // ties between ranks held once: lowest rank first (2..K)
            "'KS QH JD 2C',2C",
            "'KS QH JD QC',JD",
            // two pairs: lowest rank first, then clubs, diamonds, hearts, spades
            "'KS KH QD QC',QC",
            "'JS JH QD QC',JH",
            "'KS KH KD KC',KC",
            // a three of a kind and a single: the single
            "'5S 5H 5D 4C',4C",
            // the Ace is above the King in the rank order
            "'AS KH KD KC',AS"})
    void defaultPassIsTheRankHeldFewest(String hand, String expected) {
        assertEquals(c(expected), KiloDeMerdeRules.defaultPass(cards(hand)));
    }
}
