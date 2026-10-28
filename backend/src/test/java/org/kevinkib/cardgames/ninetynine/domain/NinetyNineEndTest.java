package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.GameFinished;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.C;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.c;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

/** Spec sections 3.3, 3.4 and examples 2, 7 and 8 of 6.2. */
class NinetyNineEndTest {

    /** C plays a 9 at 95, passing the turn to A, whose 7, 8, J are stuck. */
    private static NinetyNineBuilder aboutToBustA() {
        return game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH");
    }

    @Test
    void example2_aStuckSeatPlaysACardOverNinetyNineAndLosesALife() throws NinetyNineException {
        NinetyNine game = aboutToBustA().build();
        game.play(C, c("9H"), null);
        assertEquals(Phase.PLAYING, game.phase());

        List<NinetyNineEvent> events = game.play(A, c("7H"), null);

        assertEquals(new Busted(A, 102, NinetyNineBuilder.cards("8H JH"), 2), events.get(1));
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(2, game.lives().get(A));
        assertEquals(3, game.lives().get(B));
        assertEquals(new Busted(A, 102, NinetyNineBuilder.cards("8H JH"), 2), game.lastBust().orElseThrow());
        assertEquals(2, events.size());
        assertFalse(game.isFinished());
    }

    @Test
    void example2_theBustedSeatStartsTheNextRound() throws NinetyNineException {
        NinetyNine game = aboutToBustA().build();
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);

        game.nextRound();

        assertEquals(A, game.currentSeat());
    }

    @Test
    void theRemainingHandStaysInThePlayersHandUntilTheNextDeal() throws NinetyNineException {
        NinetyNine game = aboutToBustA().build();
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);

        assertEquals(NinetyNineBuilder.cards("8H JH"), game.hand(A));
    }

    @Test
    void example7_aSeatAtOneLifeIsEliminatedWithItsPlace() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(A).lives(B, 1).hand(A, "9H 2H 3H").hand(B, "7H 8H JH").build();
        game.play(A, c("9H"), null);

        List<NinetyNineEvent> events = game.play(B, c("7H"), null);

        assertEquals(new Busted(B, 102, NinetyNineBuilder.cards("8H JH"), 0), events.get(1));
        assertEquals(new Eliminated(B, 3), events.get(2));
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(Map.of(B, 3), game.places());
        assertEquals(3, game.place(B));
        assertEquals(List.of(A, C), game.liveSeats());
        game.nextRound();
        assertEquals(C, game.currentSeat());
    }

    @Test
    void anEliminatedSeatKeepsBeingShownWithoutCards() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(A).lives(B, 1).hand(A, "9H 2H 3H").hand(B, "7H 8H JH").build();
        game.play(A, c("9H"), null);
        game.play(B, c("7H"), null);
        game.nextRound();

        assertEquals(0, game.lives().get(B));
        assertEquals(0, game.handSizes().get(B));
        assertEquals(List.of(), game.hand(B));
        assertEquals(List.of(A, B, C), game.getPlayerIds());
    }

    @Test
    void placeOfASeatStillInTheGameIsRefused() {
        NinetyNine game = game(3).build();

        assertThrows(NotEliminatedException.class, () -> game.place(A));
    }

    @Test
    void normalModeEndsWhenOnlyOneSeatHasLivesLeft() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(A).lives(B, 0).lives(C, 1).hand(A, "9H 2H 3H")
                .hand(C, "7H 8H JH").build();
        game.play(A, c("9H"), null);

        List<NinetyNineEvent> events = game.play(C, c("7H"), null);

        assertEquals(new Eliminated(C, 2), events.get(2));
        GameFinished finished = (GameFinished) events.get(3);
        assertEquals(new NinetyNineResult(Map.of(A, 1, B, 3, C, 2), NinetyNineResult.Reason.LAST_STANDING),
                finished.result());
        assertEquals(Phase.FINISHED, game.phase());
        assertTrue(game.isFinished());
        assertEquals(finished.result(), game.result().orElseThrow());
        assertEquals(Map.of(A, 1, B, 3, C, 2), game.places());
    }

    @Test
    void aFinishedGameRefusesEveryCommand() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(A).lives(B, 0).lives(C, 1).hand(A, "9H 2H 3H")
                .hand(C, "7H 8H JH").build();
        game.play(A, c("9H"), null);
        game.play(C, c("7H"), null);
        int version = game.version();

        assertThrows(FinishedGameException.class, () -> game.play(A, c("2H"), null));
        assertThrows(FinishedGameException.class, game::nextRound);
        assertEquals(version, game.version());
    }

    @Test
    void example8_quickModeEndsAtTheFirstEliminationAndRanksByLives() throws NinetyNineException {
        NinetyNine game = game(3).quick().total(95).current(C).lives(A, 1).lives(B, 3).lives(C, 2)
                .hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();

        game.play(C, c("9H"), null);

        List<NinetyNineEvent> events = game.play(A, c("7H"), null);

        GameFinished finished = (GameFinished) events.get(events.size() - 1);
        assertEquals(new NinetyNineResult(Map.of(B, 1, C, 2, A, 3), NinetyNineResult.Reason.QUICK),
                finished.result());
        assertEquals(Phase.FINISHED, game.phase());
    }

    @Test
    void example8_quickModeSharesThePlaceBetweenEqualLives() throws NinetyNineException {
        NinetyNine game = game(3).quick().total(95).current(C).lives(A, 1).lives(B, 2).lives(C, 2)
                .hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();

        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);

        assertEquals(Map.of(B, 1, C, 1, A, 3), game.result().orElseThrow().places());
    }

    @Test
    void quickModeUsesCompetitionRankingAfterASharedPlace() throws NinetyNineException {
        NinetyNine game = game(4).quick().total(95).lives(A, 1).lives(B, 3).lives(C, 3)
                .lives(seat3(), 1).current(seat3()).hand(seat3(), "9H 2H 3H").hand(A, "7H 8H JH").build();

        game.play(seat3(), c("9H"), null);
        game.play(A, c("7H"), null);

        // B and C share 1st; D (1 life) is 3rd; A busted last
        assertEquals(Map.of(B, 1, C, 1, seat3(), 3, A, 4), game.result().orElseThrow().places());
    }

    @Test
    void aQuickModeBustThatDoesNotEliminateDoesNotEndTheGame() throws NinetyNineException {
        NinetyNine game = game(3).quick().total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();

        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertTrue(game.result().isEmpty());
    }

    @Test
    void twoPlayersInQuickModeEndWithTheOtherAsWinner() throws NinetyNineException {
        NinetyNine game = game(2).quick().total(95).current(A).lives(B, 1).hand(A, "9H 2H 3H")
                .hand(B, "7H 8H JH").build();

        game.play(A, c("9H"), null);
        game.play(B, c("7H"), null);

        assertEquals(Map.of(A, 1, B, 2), game.result().orElseThrow().places());
        assertEquals(NinetyNineResult.Reason.QUICK, game.result().orElseThrow().reason());
    }

    private static PlayerId seat3() {
        return NinetyNineBuilder.seat(3);
    }
}
