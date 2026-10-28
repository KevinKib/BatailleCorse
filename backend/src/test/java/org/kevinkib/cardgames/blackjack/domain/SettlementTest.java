package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.A;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.B;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.C;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.D;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

/** Every row of the settlement table of the spec (section 3.8). */
class SettlementTest {

    private static PlayerHand hand(String codes, int bet, boolean fromSplit) {
        return new PlayerHand(cards(codes), bet, false, fromSplit, false, PlayerHand.Status.STOOD);
    }

    private static int net(String player, int bet, String dealer) {
        return Settlement.net(hand(player, bet, false), HandValue.of(cards(dealer), false));
    }

    @Test
    void aBustedPlayerLosesTheBetEvenWhenTheDealerBusts() {
        assertEquals(-10, net("10S 6D 8C", 10, "10H 6C 9D"));
        assertEquals(-10, net("10S 6D 8C", 10, "10H 9C"));
    }

    @ParameterizedTest
    @CsvSource({"10,15", "5,7", "1,1", "3,4", "50,75", "100,150"})
    void aBlackjackPaysThreeToTwoRoundedDown(int bet, int expected) {
        assertEquals(expected, net("AS KD", bet, "10H 9C"));
    }

    @Test
    void aBlackjackAgainstADealerBlackjackIsAPush() {
        assertEquals(0, net("AS KD", 10, "AH QC"));
    }

    @Test
    void aDealerBlackjackBeatsAnyOtherHandEvenA21() {
        assertEquals(-10, net("10S 9D", 10, "AH QC"));
        assertEquals(-10, net("7S 7D 7C", 10, "AH QC"));
    }

    @Test
    void aWinAgainstABustedDealerPaysOneToOne() {
        assertEquals(10, net("10S 2D", 10, "10H 6C 9D"));
    }

    @Test
    void aHigherTotalWinsEqualTotalsPushAndALowerTotalLoses() {
        assertEquals(10, net("10S 9D", 10, "10H 8C"));
        assertEquals(0, net("10S 8D", 10, "10H 8C"));
        assertEquals(-10, net("10S 7D", 10, "10H 8C"));
    }

    @Test
    void aTwentyOneOfThreeCardsIsNotABlackjack() {
        assertEquals(10, net("7S 7D 7C", 10, "10H 9C"));
    }

    @Test
    void aTwentyOneAfterASplitPaysOneToOne() {
        assertEquals(10, Settlement.net(hand("AS KD", 10, true), HandValue.of(cards("10H 9C"), false)));
    }

    @Test
    void aTwentyOneAfterASplitDoesNotPushAgainstADealerBlackjack() {
        assertEquals(-10, Settlement.net(hand("AS KD", 10, true), HandValue.of(cards("AH QC"), false)));
    }

    @Test
    void aDoubledHandSettlesOnItsDoubledBet() {
        PlayerHand doubled = new PlayerHand(cards("5S 6D 9H"), 20, true, false, false, PlayerHand.Status.STOOD);
        assertEquals(20, Settlement.net(doubled, HandValue.of(cards("10H 8C"), false)));
        PlayerHand doubledLoser = new PlayerHand(cards("5S 6D 8H"), 20, true, false, false, PlayerHand.Status.STOOD);
        assertEquals(-20, Settlement.net(doubledLoser, HandValue.of(cards("10H QC"), false)));
    }

    @Test
    void theBestHandsAreTheLargestPositiveTotalNets() {
        Map<PlayerId, Integer> nets = new LinkedHashMap<>();
        nets.put(A, 10);
        nets.put(B, 15);
        nets.put(C, 15);
        nets.put(D, -5);
        assertEquals(List.of(B, C), Settlement.bestSeats(nets));
    }

    @Test
    void thereIsNoBestHandWhenNobodyGained() {
        Map<PlayerId, Integer> nets = new LinkedHashMap<>();
        nets.put(A, 0);
        nets.put(B, -5);
        assertEquals(List.of(), Settlement.bestSeats(nets));
    }
}
