package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BetPlaced;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.A;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.B;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.C;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.event;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.game;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.has;

class BlackjackBetTest {

    private static final String SHOE = "10S 8C 9D 6H 10H 8D 7S 10C";

    // Worked example 1
    @Test
    void betsAreCheckedAgainstTheMinimumTheMaximumAndTheDealWaitsForEveryone() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        game.bet(A, 10);
        assertThrows(InvalidBetException.class, () -> game.bet(B, 0));
        assertThrows(InvalidBetException.class, () -> game.bet(B, 60));
        assertEquals(100, game.seat(B).stack());
        game.bet(B, 50);
        assertEquals(50, game.seat(B).stack());
        assertEquals(Phase.BETTING, game.phase());

        List<BlackjackEvent> events = game.bet(C, 5);

        assertEquals(Phase.PLAYING, game.phase());
        assertTrue(has(events, HandDealt.class));
        assertEquals(List.of(10, 50, 5), event(events, HandDealt.class).bets().values().stream().toList());
    }

    @Test
    void aBetLeavesTheStackAtOnceAndIsReportedAsPlaced() throws Exception {
        Blackjack game = game(2).shoe(SHOE).build();

        List<BlackjackEvent> events = game.bet(A, 30);

        assertEquals(List.of(new BetPlaced(A)), events);
        assertEquals(70, game.seat(A).stack());
        assertTrue(game.seat(A).betPlaced());
        assertEquals(30, game.seat(A).bet());
        assertFalse(game.seat(B).betPlaced());
    }

    @Test
    void theBetIsCappedByTheOptionAndByTheStack() throws Exception {
        Blackjack capped = game(2).options(BlackjackOptions.Hands.TEN, BlackjackOptions.MaxBet.TWENTY_FIVE).build();
        assertThrows(InvalidBetException.class, () -> capped.bet(A, 26));
        capped.bet(A, 25);

        Blackjack poor = game(2).stacks(30, 100).build();
        assertThrows(InvalidBetException.class, () -> poor.bet(A, 31));
        poor.bet(A, 30);
        assertEquals(0, poor.seat(A).stack());
        assertEquals(50, poor.maxBetFor(B));
    }

    @Test
    void theLargestOptionAllowsAnAllIn() throws Exception {
        Blackjack game = game(2).options(BlackjackOptions.Hands.TEN, BlackjackOptions.MaxBet.HUNDRED).build();
        game.bet(A, 100);
        assertEquals(0, game.seat(A).stack());
    }

    @Test
    void aSeatCannotBetTwiceOnTheSameHand() throws Exception {
        Blackjack game = game(2).build();
        game.bet(A, 10);
        assertThrows(AlreadyBetException.class, () -> game.bet(A, 10));
    }

    @Test
    void aBetOutsideTheBettingPhaseIsRefused() throws Exception {
        Blackjack game = game(2).shoe(SHOE).build();
        BlackjackBuilder.bets(game, 10, 10);
        assertThrows(WrongPhaseException.class, () -> game.bet(A, 10));
    }

    @Test
    void anEliminatedSeatCannotBet() {
        Blackjack game = game(3).stacks(100, 0, 100).build();
        assertThrows(EliminatedSeatException.class, () -> game.bet(B, 1));
    }

    @Test
    void theDealDoesNotWaitForAnEliminatedSeat() throws Exception {
        Blackjack game = game(3).stacks(100, 0, 100).shoe(SHOE).build();
        game.bet(A, 10);
        List<BlackjackEvent> events = game.bet(C, 10);
        assertTrue(has(events, HandDealt.class));
        assertTrue(game.seat(B).hands().isEmpty());
    }

    @Test
    void anUnknownSeatIsAProgrammingError() {
        Blackjack game = game(2).build();
        assertThrows(IllegalArgumentException.class, () -> game.bet(new PlayerId(5), 10));
    }

    @Test
    void everyAcceptedBetBumpsTheVersionAndARefusedOneChangesNothing() throws Exception {
        Blackjack game = game(2).shoe(SHOE).build();
        int version = game.version();

        assertThrows(InvalidBetException.class, () -> game.bet(A, 0));
        assertEquals(version, game.version());
        assertEquals(100, game.seat(A).stack());
        assertFalse(game.seat(A).betPlaced());

        game.bet(A, 10);
        assertEquals(version + 1, game.version());
        game.bet(B, 10);
        assertEquals(version + 2, game.version());
    }

    @Test
    void theSeatRemembersItsLastBet() throws Exception {
        Blackjack game = game(2).shoe(SHOE).build();
        assertTrue(game.lastBet(A).isEmpty());
        game.bet(A, 12);
        assertEquals(12, game.lastBet(A).orElseThrow());
    }

    @Test
    void aBetOnAFinishedGameIsRefused() throws Exception {
        Blackjack game = game(1).options(BlackjackOptions.Hands.TEN, BlackjackOptions.MaxBet.HUNDRED)
                .shoe("10S 10H 6D 9C").build();
        game.bet(A, 100);
        game.stand(A);
        assertTrue(game.isFinished());
        assertThrows(FinishedGameException.class, () -> game.bet(A, 1));
    }

    @Test
    void theGameIdIsKept() {
        GameId id = GameId.generate();
        Blackjack game = new Blackjack(id, 2, new ScriptedShoeSource(new RandomShoeSource()),
                BlackjackOptions.defaults());
        assertEquals(id, game.getId());
        assertEquals(List.of(A, B), game.getPlayerIds());
        assertEquals(Phase.BETTING, game.phase());
        assertEquals(1, game.handNumber());
        assertEquals(10, game.handsTotal());
        assertEquals(A, game.button());
    }
}
