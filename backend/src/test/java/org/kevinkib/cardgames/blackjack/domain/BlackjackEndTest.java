package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.blackjack.domain.event.Eliminated;
import org.kevinkib.cardgames.blackjack.domain.event.GameFinished;

import java.util.List;
import java.util.Map;

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

class BlackjackEndTest {

    // Everyone holds 18 against a dealer 18: a push for all.
    private static final String ALL_PUSH_3 = "10S 10D 10C 10H 8S 8D 8C 8H";

    // Worked example 10. Hand 1: A 16 (loses its last 10 points), B 18 (pushes), C 19 (wins). Hand 2 (B, C): B 16
    // loses its last 20 points, C 20 wins.
    @Test
    void aPlayerAtZeroIsEliminatedAndTheGameEndsWhenOnePlayerIsLeft() throws Exception {
        Blackjack game = game(3).stacks(10, 20, 100)
                .shoe("10S 10D 10C 10H 6S 8D 9C 8H")
                .shoeForHand(2, "10S 10C 10H 6D 10D 9S")
                .build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);
        List<BlackjackEvent> events = game.stand(C);

        assertEquals(new Eliminated(A, 1), event(events, Eliminated.class));
        assertTrue(game.seat(A).eliminated());
        assertEquals(1, game.seat(A).eliminatedAtHand());
        assertEquals(0, game.seat(A).stack());
        assertEquals(20, game.seat(B).stack());
        assertEquals(110, game.seat(C).stack());
        assertEquals(Phase.BETTING, game.phase());
        assertEquals(2, game.handNumber());
        assertEquals(B, game.button());
        assertFalse(game.isFinished());

        game.bet(B, 20);
        game.bet(C, 10);
        game.stand(B);
        events = game.stand(C);

        assertTrue(game.isFinished());
        assertEquals(Phase.FINISHED, game.phase());
        BlackjackResult result = event(events, GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.LAST_STANDING, result.reason());
        assertEquals(Map.of(C, 1, B, 2, A, 3), result.places());
        assertEquals(Map.of(A, 0, B, 0, C, 120), result.stacks());
        assertEquals(result, game.result().orElseThrow());
        assertEquals(3, game.seat(A).place());
        assertEquals(1, game.seat(C).place());
    }

    // Worked example 11
    @Test
    void afterTheLastHandEqualStacksShareThePlace() throws Exception {
        Blackjack game = game(3).stacks(140, 60, 140).handNumber(10).shoe(ALL_PUSH_3).build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);

        List<BlackjackEvent> events = game.stand(C);

        BlackjackResult result = event(events, GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.HANDS_PLAYED, result.reason());
        assertEquals(Map.of(A, 1, C, 1, B, 3), result.places());
        assertEquals(Map.of(A, 140, B, 60, C, 140), result.stacks());
        assertEquals(Phase.FINISHED, game.phase());
    }

    @Test
    void theGameEndsAfterTheNumberOfHandsOfTheOption() throws Exception {
        Blackjack game = game(2).options(BlackjackOptions.Hands.FIVE, BlackjackOptions.MaxBet.FIFTY).handNumber(5)
                .shoe("10S 10D 10H 8S 8D 8H").build();
        bets(game, 10, 10);
        game.stand(A);
        game.stand(B);
        assertEquals(5, game.handsTotal());
        assertTrue(game.isFinished());
    }

    // Worked example 12
    @Test
    void aSoloPlayerWhoLosesEverythingEndsBrokeWithNoPlace() throws Exception {
        Blackjack game = game(1).options(BlackjackOptions.Hands.TEN, BlackjackOptions.MaxBet.HUNDRED)
                .shoe("10S 10H 6D 9C").build();
        bets(game, 100);

        List<BlackjackEvent> events = game.stand(A);

        BlackjackResult result = event(events, GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.BROKE, result.reason());
        assertEquals(Map.of(), result.places());
        assertEquals(Map.of(A, 0), result.stacks());
        assertTrue(game.isSolo());
        assertTrue(game.isFinished());
    }

    @Test
    void aSoloGameEndsAfterTheLastHandWithItsFinalStack() throws Exception {
        Blackjack game = game(1).stacks(123).handNumber(10).shoe("10S 10H 8D 8C").build();
        bets(game, 10);

        List<BlackjackEvent> events = game.stand(A);

        BlackjackResult result = event(events, GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.HANDS_PLAYED, result.reason());
        assertEquals(Map.of(), result.places());
        assertEquals(123, result.stacks().get(A));
        assertEquals(23, result.stacks().get(A) - BlackjackOptions.STARTING_STACK);
    }

    @Test
    void aSoloPlayerBrokeOnTheLastHandEndsBroke() throws Exception {
        Blackjack game = game(1).stacks(10).handNumber(10).shoe("10S 10H 6D 9C").build();
        bets(game, 10);

        List<BlackjackEvent> events = game.stand(A);

        assertEquals(BlackjackResult.Reason.BROKE, event(events, GameFinished.class).result().reason());
    }

    @Test
    void playersEliminatedOnTheSameHandShareThePlaceAndEveryoneBeingOutEndsTheGame() throws Exception {
        Blackjack game = game(2).stacks(10, 10).shoe("10S 10D 10H 6S 7D 8H").build();
        bets(game, 10, 10);
        game.stand(A);

        List<BlackjackEvent> events = game.stand(B);

        BlackjackResult result = event(events, GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.LAST_STANDING, result.reason());
        assertEquals(Map.of(A, 1, B, 1), result.places());
    }

    @Test
    void laterEliminationsRankHigherAndLivePlayersRankAboveEveryoneEliminated() throws Exception {
        // Hand 4 of 5: A (16) loses its last 10 and is out. Hand 5 (button B): B (16) loses its last 20 and is out,
        // C pushes and D wins: C and D share 1st on 110, then B (later) above A.
        Blackjack game = game(4).stacks(10, 20, 100, 100).handNumber(4)
                .options(BlackjackOptions.Hands.FIVE, BlackjackOptions.MaxBet.FIFTY)
                .shoe("10S 10D 10C 10S 10H 6S 8D 9C 8S 8H")
                .shoeForHand(5, "10S 10C 10D 10H 6D 8C 9S 8H")
                .build();
        bets(game, 10, 10, 10, 10);
        game.stand(A);
        game.stand(B);
        game.stand(C);
        game.stand(BlackjackBuilder.seat(3));
        assertEquals(4, game.seat(A).eliminatedAtHand());

        game.bet(B, 20);
        game.bet(C, 10);
        game.bet(BlackjackBuilder.seat(3), 10);
        game.stand(B);
        game.stand(C);
        game.stand(BlackjackBuilder.seat(3));

        assertEquals(5, game.seat(B).eliminatedAtHand());
        BlackjackResult result = game.result().orElseThrow();
        assertEquals(BlackjackResult.Reason.HANDS_PLAYED, result.reason());
        assertEquals(Map.of(C, 1, BlackjackBuilder.seat(3), 1, B, 3, A, 4), result.places());
        assertEquals(Map.of(A, 0, B, 0, C, 110, BlackjackBuilder.seat(3), 110), result.stacks());
    }

    @Test
    void aFinishedGameRefusesEveryCommand() throws Exception {
        Blackjack game = game(1).stacks(10).shoe("10S 10H 6D 9C").build();
        bets(game, 10);
        game.stand(A);
        assertTrue(game.isFinished());
        int version = game.version();

        assertThrows(FinishedGameException.class, () -> game.bet(A, 1));
        assertThrows(FinishedGameException.class, () -> game.hit(A));
        assertThrows(FinishedGameException.class, () -> game.stand(A));
        assertThrows(FinishedGameException.class, () -> game.doubleDown(A));
        assertThrows(FinishedGameException.class, () -> game.split(A));
        assertEquals(version, game.version());
        assertEquals(List.of(), game.availableActions(A));
    }

    // Worked example 15
    @Test
    void theButtonMovesOneLiveSeatEachHandAndSkipsEliminatedSeats() throws Exception {
        Blackjack game = game(3).stacks(100, 10, 100)
                .shoe(ALL_PUSH_3)
                .shoeForHand(2, "10S 10C 10D 10H 6D 8C 8D 8H")
                .build();
        assertEquals(1, game.handNumber());
        assertEquals(A, game.button());
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);
        game.stand(C);

        assertEquals(2, game.handNumber());
        assertEquals(B, game.button());
        bets(game, 10, 10, 10);
        assertEquals(B, game.currentSeat());
        game.stand(B);
        game.stand(C);
        game.stand(A);

        assertTrue(game.seat(B).eliminated());
        assertEquals(3, game.handNumber());
        assertEquals(C, game.button());
    }
}
