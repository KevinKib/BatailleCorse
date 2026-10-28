package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.DealerBlackjack;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.A;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.B;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.C;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.bets;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.event;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.game;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.has;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.c;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

class BlackjackDealTest {

    // Worked example 2: the deal order is a card to each seat from the button, the up card, a second card to each
    // seat, then the hole card.
    @Test
    void theDealGoesFromTheButtonThroughTheSeatsThenTheUpCardThenTheHoleCard() throws Exception {
        Blackjack game = game(3).shoe("AS KC QD 5H 9S 7C 6D 10H").build();

        List<BlackjackEvent> events = bets(game, 10, 10, 10);

        assertEquals(cards("AS 9S"), game.seat(A).hands().get(0).cards());
        assertEquals(cards("KC 7C"), game.seat(B).hands().get(0).cards());
        assertEquals(cards("QD 6D"), game.seat(C).hands().get(0).cards());
        assertEquals(c("5H"), game.dealerUpCard());
        HandDealt dealt = event(events, HandDealt.class);
        assertEquals(1, dealt.handNumber());
        assertEquals(A, dealt.button());
        assertEquals(c("5H"), dealt.upCard());
        assertEquals(cards("AS 9S"), dealt.seatCards().get(A));
    }

    @Test
    void withTheButtonOnBTheDealStartsAtBAndWrapsAround() throws Exception {
        Blackjack game = game(3).button(1).shoe("AS KC QD 5H 9S 7C 6D 10H").build();

        List<BlackjackEvent> events = bets(game, 10, 10, 10);

        assertEquals(cards("AS 9S"), game.seat(B).hands().get(0).cards());
        assertEquals(cards("KC 7C"), game.seat(C).hands().get(0).cards());
        assertEquals(cards("QD 6D"), game.seat(A).hands().get(0).cards());
        assertEquals(B, event(events, HandDealt.class).button());
        assertEquals(B, game.button());
        assertEquals(B, game.currentSeat());
    }

    @Test
    void theHoleCardStaysHiddenUntilTheDealerPlays() throws Exception {
        // Worked example 5: the up card is a 10, the peek finds nothing, the hole card stays down.
        Blackjack game = game(2).shoe("10S 8C 10H 6D 9S 7C").build();

        List<BlackjackEvent> events = bets(game, 10, 10);

        assertEquals(Phase.PLAYING, game.phase());
        assertFalse(has(events, DealerBlackjack.class));
        assertFalse(game.holeCardShown());
        assertEquals(List.of(c("10H")), game.dealerCards());
        assertEquals(c("10H"), game.dealerUpCard());
        assertEquals(A, game.currentSeat());
    }

    // Worked example 3
    @ParameterizedTest
    @CsvSource({"10,15", "5,7"})
    void aPlayerBlackjackIsSkippedAndPaysThreeToTwoRoundedDown(int bet, int win) throws Exception {
        // A: AS KS (blackjack); B: 5H 10H (15); C: 6C 10D (16); up 9D; hole 2C (11): the dealer draws 10S: 21.
        Blackjack game = game(3).shoe("AS 5H 6C 9D KS 10H 10D 2C 10S").build();
        bets(game, bet, 10, 10);

        assertEquals(PlayerHand.Status.BLACKJACK, game.seat(A).hands().get(0).status());
        assertEquals(B, game.currentSeat());
        game.stand(B);
        List<BlackjackEvent> events = game.stand(C);

        assertEquals(win, event(events, HandSettled.class).nets().get(A));
        assertEquals(100 + win, game.seat(A).stack());
        assertEquals(90, game.seat(B).stack());
        assertEquals(90, game.seat(C).stack());
        assertEquals(cards("9D 2C 10S"), game.lastHand().dealerCards());
    }

    // Worked example 4
    @Test
    void aDealerBlackjackEndsTheHandAtOnceAndOnlyAPlayerBlackjackPushes() throws Exception {
        // A: AH KH (blackjack); B: 9C 7C (16); C: 8C 6C (14); up AS; hole KS.
        Blackjack game = game(3).shoe("AH 9C 8C AS KH 7C 6C KS").build();

        List<BlackjackEvent> events = bets(game, 10, 10, 10);

        assertEquals(c("KS"), event(events, DealerBlackjack.class).holeCard());
        assertFalse(has(events, DealerPlayed.class));
        assertEquals(0, event(events, HandSettled.class).nets().get(A));
        assertEquals(-10, event(events, HandSettled.class).nets().get(B));
        assertEquals(-10, event(events, HandSettled.class).nets().get(C));
        assertEquals(100, game.seat(A).stack());
        assertEquals(90, game.seat(B).stack());
        assertEquals(90, game.seat(C).stack());
        // No PLAYING phase: the next hand's betting is open and the last hand stays readable.
        assertEquals(Phase.BETTING, game.phase());
        assertEquals(2, game.handNumber());
        assertEquals(cards("AS KS"), game.lastHand().dealerCards());
    }

    @Test
    void aDealerShowingATenAlsoPeeks() throws Exception {
        Blackjack game = game(1).shoe("9S KD 7C AH").build();

        List<BlackjackEvent> events = bets(game, 10);

        assertTrue(has(events, DealerBlackjack.class));
        assertEquals(90, game.seat(A).stack());
    }

    @Test
    void anUpCardBelowTenNeverPeeksEvenWithAnAceInTheHole() throws Exception {
        // up 9, hole A: 20, not a blackjack and nothing is revealed.
        Blackjack game = game(1).shoe("9S 9D 7C AH").build();

        List<BlackjackEvent> events = bets(game, 10);

        assertFalse(has(events, DealerBlackjack.class));
        assertEquals(Phase.PLAYING, game.phase());
    }

    @Test
    void whenEverySeatHasABlackjackTheDealerPlaysAtOnce() throws Exception {
        // Solo: A: AS KS; up 9H; hole 7C (16): the dealer draws 5D: 21. The blackjack still wins.
        Blackjack game = game(1).shoe("AS 9H KS 7C 5D").build();

        List<BlackjackEvent> events = bets(game, 10);

        assertEquals(cards("9H 7C 5D"), game.lastHand().dealerCards());
        assertEquals(15, event(events, HandSettled.class).nets().get(A));
        assertEquals(115, game.seat(A).stack());
        assertEquals(Phase.BETTING, game.phase());
        assertEquals(2, game.handNumber());
    }

    @Test
    void theLastHandStaysOnTheTableDuringTheNextBetting() throws Exception {
        Blackjack game = game(1).shoe("AS 9H KS 7C 5D").build();
        bets(game, 10);

        assertEquals(1, game.lastHand().handNumber());
        assertEquals(15, game.lastHand().nets().get(A));
        assertEquals(List.of(A), game.lastHand().bestSeats());
        assertTrue(game.seat(A).hands().isEmpty());
        assertTrue(game.dealerCards().isEmpty());
        assertFalse(game.seat(A).betPlaced());
    }
}
