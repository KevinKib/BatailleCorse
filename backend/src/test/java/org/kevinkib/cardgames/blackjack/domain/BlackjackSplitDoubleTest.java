package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.Doubled;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.SplitHand;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class BlackjackSplitDoubleTest {

    // Worked example 6. A: 10S 10H (20); B: 8C 8D; C: 9D 7S (16); up 6H; hole 10C (16). B splits and draws 3H for
    // its first hand, doubles it (KS: 21), and its second hand gets 5D only then.
    private static final String SPLIT_SHOE = "10S 8C 9D 6H 10H 8D 7S 10C 3H KS 5D";

    @Test
    void aSplitMakesTwoHandsAndTheSecondOneWaitsForItsCard() throws Exception {
        Blackjack game = game(3).shoe(SPLIT_SHOE).build();
        bets(game, 10, 10, 10);
        game.stand(A);
        assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT), game.availableActions(B));

        List<BlackjackEvent> events = game.split(B);

        assertEquals(new SplitHand(B, cards("8C 3H 8D")), event(events, SplitHand.class));
        assertEquals(80, game.seat(B).stack());
        List<PlayerHand> hands = game.seat(B).hands();
        assertEquals(2, hands.size());
        assertEquals(cards("8C 3H"), hands.get(0).cards());
        assertEquals(PlayerHand.Status.ACTIVE, hands.get(0).status());
        assertEquals(cards("8D"), hands.get(1).cards());
        assertEquals(PlayerHand.Status.WAITING, hands.get(1).status());
        assertEquals(10, hands.get(1).bet());
        assertTrue(hands.get(0).fromSplit());
        assertEquals(0, game.seat(B).activeHandIndex());
        assertEquals(B, game.currentSeat());
    }

    @Test
    void aDoubleOnTheFirstSplitHandStandsItAndTheSecondHandIsOnlyDealtNow() throws Exception {
        Blackjack game = game(3).shoe(SPLIT_SHOE).build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.split(B);
        assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE), game.availableActions(B));

        List<BlackjackEvent> events = game.doubleDown(B);

        assertEquals(new Doubled(B, 0, c("KS")), event(events, Doubled.class));
        assertEquals(new CardDrawn(B, 1, c("5D"), 13), event(events, CardDrawn.class));
        PlayerHand first = game.seat(B).hands().get(0);
        assertEquals(cards("8C 3H KS"), first.cards());
        assertEquals(20, first.bet());
        assertTrue(first.doubled());
        assertEquals(PlayerHand.Status.STOOD, first.status());
        assertEquals(cards("8D 5D"), game.seat(B).hands().get(1).cards());
        assertEquals(PlayerHand.Status.ACTIVE, game.seat(B).hands().get(1).status());
        assertEquals(1, game.seat(B).activeHandIndex());
        assertEquals(70, game.seat(B).stack());
        assertEquals(B, game.currentSeat());
    }

    @Test
    void aSecondSplitIsRefused() throws Exception {
        Blackjack game = game(3).shoe(SPLIT_SHOE).build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.split(B);
        game.doubleDown(B);
        int version = game.version();

        assertFalse(game.availableActions(B).contains(Action.SPLIT));
        assertThrows(ActionNotAllowedException.class, () -> game.split(B));

        assertEquals(version, game.version());
        assertEquals(70, game.seat(B).stack());
    }

    // Worked example 7. A: 10S 10H; B: 10D 10C; C: AH AD; up 6H; hole 10S (16). C splits Aces: KC and 9S. The
    // dealer draws 4D: 20.
    @Test
    void splitAcesGetOneCardEachStandAtOnceAndA21IsNotABlackjack() throws Exception {
        Blackjack game = game(3).shoe("10S 10D AH 6H 10H 10C AD 10S KC 9S 4D").build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);

        List<BlackjackEvent> events = game.split(C);

        assertEquals(new SplitHand(C, cards("AH KC AD 9S")), event(events, SplitHand.class));
        assertTrue(has(events, Stood.class));
        HandSettled settled = event(events, HandSettled.class);
        assertEquals(10, settled.nets().get(C));
        List<LastHand.SettledHand> hands = game.lastHand().seats().get(C);
        assertEquals(PlayerHand.Status.STOOD, hands.get(0).hand().status());
        assertFalse(hands.get(0).hand().value().blackjack());
        assertEquals(10, hands.get(0).net());
        assertEquals(0, hands.get(1).net());
        assertEquals(110, game.seat(C).stack());
        assertEquals(100, game.seat(A).stack());
    }

    @Test
    void whenTheFirstSplitHandStandsOnTwentyOneTheSecondIsDealtAtOnce() throws Exception {
        Blackjack game = game(1).shoe("8C 6H 8D 10S 3H 10D 5C").build();
        bets(game, 10);
        game.split(A);

        List<BlackjackEvent> events = game.hit(A);

        assertEquals(new CardDrawn(A, 0, c("10D"), 21), event(events, CardDrawn.class));
        assertEquals(new Stood(A, 0), event(events, Stood.class));
        assertEquals(1, game.seat(A).activeHandIndex());
        assertEquals(cards("8D 5C"), game.seat(A).hands().get(1).cards());
    }

    @Test
    void whenTheFirstSplitHandBustsTheSecondIsDealtAtOnce() throws Exception {
        Blackjack game = game(1).shoe("8C 6H 8D 10S 10H 10D 2C").build();
        bets(game, 10);
        game.split(A);

        List<BlackjackEvent> events = game.hit(A);

        assertEquals(new Busted(A, 0), event(events, Busted.class));
        assertEquals(PlayerHand.Status.BUST, game.seat(A).hands().get(0).status());
        assertEquals(1, game.seat(A).activeHandIndex());
        assertEquals(cards("8D 2C"), game.seat(A).hands().get(1).cards());
        assertEquals(PlayerHand.Status.ACTIVE, game.seat(A).hands().get(1).status());
    }

    @Test
    void twoTenValueCardsOfDifferentRanksCanSplit() throws Exception {
        Blackjack game = game(1).shoe("KS 6H 10D 9C 3H 4D").build();
        bets(game, 10);
        assertTrue(game.availableActions(A).contains(Action.SPLIT));

        game.split(A);

        assertEquals(cards("KS 3H"), game.seat(A).hands().get(0).cards());
        assertEquals(cards("10D"), game.seat(A).hands().get(1).cards());
    }

    @Test
    void twoCardsOfDifferentValuesCannotSplit() throws Exception {
        Blackjack game = game(1).shoe("10S 6H 9D 9C").build();
        bets(game, 10);

        assertFalse(game.availableActions(A).contains(Action.SPLIT));
        assertThrows(ActionNotAllowedException.class, () -> game.split(A));
    }

    // Worked example 13
    @Test
    void doublingAndSplittingNeedAStackOfAtLeastTheBet() throws Exception {
        Blackjack game = game(1).stacks(15).shoe("8C 6H 8D 10S").build();
        bets(game, 10);
        int version = game.version();

        assertEquals(List.of(Action.HIT, Action.STAND), game.availableActions(A));
        assertThrows(ActionNotAllowedException.class, () -> game.doubleDown(A));
        assertThrows(ActionNotAllowedException.class, () -> game.split(A));

        assertEquals(version, game.version());
        assertEquals(5, game.seat(A).stack());
        assertEquals(1, game.seat(A).hands().size());
    }

    @Test
    void aSplitThatEmptiesTheStackLeavesNothingToDoubleWith() throws Exception {
        Blackjack game = game(1).stacks(20).shoe("8C 6H 8D 10S 3H").build();
        bets(game, 10);
        game.split(A);

        assertEquals(0, game.seat(A).stack());
        assertEquals(List.of(Action.HIT, Action.STAND), game.availableActions(A));
    }

    @Test
    void aDoubleTakesExactlyOneCardDoublesTheBetAndStands() throws Exception {
        // A: 5S 6D (11); up 6H; hole 10C (16). A doubles 9H: 20; the dealer draws 3C: 19.
        Blackjack game = game(1).shoe("5S 6H 6D 10C 9H 3C").build();
        bets(game, 10);
        assertEquals(90, game.seat(A).stack());

        List<BlackjackEvent> events = game.doubleDown(A);

        assertEquals(new Doubled(A, 0, c("9H")), event(events, Doubled.class));
        assertEquals(20, event(events, HandSettled.class).nets().get(A));
        PlayerHand hand = game.lastHand().seats().get(A).get(0).hand();
        assertEquals(20, hand.bet());
        assertTrue(hand.doubled());
        assertEquals(120, game.seat(A).stack());
    }

    @Test
    void aDoubledHandThatBustsLosesTheDoubledBet() throws Exception {
        Blackjack game = game(1).shoe("10S 6H 5D 10C 10H").build();
        bets(game, 10);

        List<BlackjackEvent> events = game.doubleDown(A);

        assertTrue(has(events, Busted.class));
        assertEquals(-20, event(events, HandSettled.class).nets().get(A));
        assertEquals(80, game.seat(A).stack());
    }

    @Test
    void aDoubleIsOnlyAllowedOnTheFirstTwoCards() throws Exception {
        Blackjack game = game(1).shoe("2S 6H 3D 10C 4H").build();
        bets(game, 10);
        game.hit(A);

        assertFalse(game.availableActions(A).contains(Action.DOUBLE));
        assertThrows(ActionNotAllowedException.class, () -> game.doubleDown(A));
    }
}
