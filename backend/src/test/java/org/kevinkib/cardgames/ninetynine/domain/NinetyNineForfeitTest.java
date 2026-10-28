package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.C;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.cards;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

class NinetyNineForfeitTest {

    @Test
    void example10_forfeitOnTurnEliminatesTheSeatAndMovesTheTurnOn() throws NinetyNineException {
        NinetyNine game = game(3).total(40).hand(A, "2H 3H 4H").build();
        int version = game.version();

        game.forfeit(A);

        assertEquals(B, game.currentSeat());
        assertEquals(0, game.lives().get(A));
        assertEquals(List.of(), game.hand(A));
        assertEquals(40, game.total());
        assertEquals(3, game.place(A));
        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(version + 1, game.version());
    }

    @Test
    void forfeitOffTurnKeepsTheTurnAndTheTotal() throws NinetyNineException {
        NinetyNine game = game(3).total(40).build();

        game.forfeit(C);

        assertEquals(A, game.currentSeat());
        assertEquals(40, game.total());
        assertEquals(List.of(A, B), game.liveSeats());
        assertEquals(3, game.place(C));
    }

    @Test
    void forfeitOnTurnFollowsTheDirection() {
        NinetyNine game = game(3).direction(Direction.COUNTER_CLOCKWISE).build();

        game.forfeit(A);

        assertEquals(C, game.currentSeat());
    }

    @Test
    void example10_whenOneLiveSeatIsLeftTheGameIsOverByForfeit() {
        NinetyNine game = game(3).build();
        game.forfeit(A);

        game.forfeit(B);

        assertTrue(game.isFinished());
        assertEquals(Phase.FINISHED, game.phase());
        assertEquals(new NinetyNineResult(Map.of(C, 1, B, 2, A, 3), NinetyNineResult.Reason.FORFEIT),
                game.result().orElseThrow());
    }

    @Test
    void forfeitOfTheLastButOneEndsAtOnce() {
        NinetyNine game = game(2).build();

        game.forfeit(B);

        assertEquals(new NinetyNineResult(Map.of(A, 1, B, 2), NinetyNineResult.Reason.FORFEIT),
                game.result().orElseThrow());
    }

    @Test
    void aForfeitedSeatRanksBelowEveryLiveSeatEvenAnEliminatedOneBefore() throws NinetyNineException {
        // B was eliminated by a bust (place 4 of 4 would be the forfeit); here C forfeits after B was eliminated
        NinetyNine game = game(4).lives(B, 0).build();

        game.forfeit(C);

        assertEquals(Map.of(B, 4, C, 3), game.places());
    }

    @Test
    void forfeitDoesNotTriggerTheQuickModeEnd() {
        NinetyNine game = game(3).quick().build();

        game.forfeit(C);

        assertFalse(game.isFinished());
        assertEquals(Phase.PLAYING, game.phase());
    }

    @Test
    void forfeitInQuickModeWithTwoSeatsLeftEndsByForfeit() {
        NinetyNine game = game(2).quick().build();

        game.forfeit(A);

        assertEquals(NinetyNineResult.Reason.FORFEIT, game.result().orElseThrow().reason());
    }

    @Test
    void forfeitMovingTheTurnToAStuckSeatDoesNotBustItYet() throws NinetyNineException {
        NinetyNine game = game(3).total(95).hand(A, "9H 2H 3H").hand(B, "7H 8H JH").build();

        game.forfeit(A);

        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(B, game.currentSeat());
        assertEquals(3, game.lives().get(B));
        assertTrue(game.lastBust().isEmpty());
        game.play(B, NinetyNineBuilder.c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(2, game.lives().get(B));
    }

    @Test
    void forfeitBetweenRoundsLetsTheNextRoundStartWithTheNextLiveSeat() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();
        game.play(C, NinetyNineBuilder.c("9H"), null);
        game.play(A, NinetyNineBuilder.c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());

        game.forfeit(A);
        game.nextRound();

        assertEquals(B, game.currentSeat());
        assertEquals(0, game.handSizes().get(A));
        assertEquals(cards("9H 9D 9C").size(), game.hand(B).size());
    }

    @Test
    void forfeitOfAnUnknownOrAlreadyOutSeatChangesNothing() {
        NinetyNine game = game(3).lives(B, 0).build();
        int version = game.version();

        game.forfeit(B);
        game.forfeit(NinetyNineBuilder.seat(9));

        assertEquals(version, game.version());
        assertFalse(game.isFinished());
    }

    @Test
    void forfeitAfterTheEndChangesNothing() {
        NinetyNine game = game(2).build();
        game.forfeit(A);
        int version = game.version();

        game.forfeit(B);

        assertEquals(version, game.version());
        assertEquals(Map.of(B, 1, A, 2), game.result().orElseThrow().places());
    }
}
