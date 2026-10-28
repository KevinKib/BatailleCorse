package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.C;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.c;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.cards;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

/** A seat with no safe card is not busted at once: it is on turn and must play a card that goes over 99. */
class NinetyNineStuckTest {

    /** C plays a 9 at 95, passing the turn to A, whose 7, 8, J are all over 99. */
    private static NinetyNineBuilder aboutToBeStuckA() {
        return game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH");
    }

    @Test
    void aSeatWithNoSafeCardStaysOnTurnInsteadOfBustingAtOnce() throws NinetyNineException {
        NinetyNine game = aboutToBeStuckA().build();

        List<NinetyNineEvent> events = game.play(C, c("9H"), null);

        assertEquals(1, events.size());
        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(A, game.currentSeat());
        assertEquals(3, game.lives().get(A));
        assertTrue(game.lastBust().isEmpty());
        assertTrue(game.isStuck());
        assertEquals(List.of(), game.safeCards(A));
        assertEquals(List.of(Action.PLAY), game.availableActions(A));
    }

    @Test
    void aSeatWithASafeCardIsNotStuck() {
        NinetyNine game = game(3).total(95).current(A).hand(A, "9H 7H JH").build();

        assertFalse(game.isStuck());
    }

    @Test
    void theStuckSeatPlaysACardThatGoesOver99ThenLosesALife() throws NinetyNineException {
        NinetyNine game = aboutToBeStuckA().build();
        game.play(C, c("9H"), null);

        List<NinetyNineEvent> events = game.play(A, c("7H"), null);

        assertEquals(new Played(A, c("7H"), null, 102, false), events.get(0));
        assertEquals(new Busted(A, 102, cards("8H JH"), 2), events.get(1));
        assertEquals(2, events.size());
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(102, game.total());
        assertEquals(2, game.lives().get(A));
        assertEquals(c("7H"), game.pileTop().orElseThrow());
        LastPlay last = game.lastPlay().orElseThrow();
        assertEquals(A, last.seat());
        assertEquals(95, last.totalBefore());
        assertEquals(102, last.totalAfter());
        assertEquals(events.get(1), game.lastBust().orElseThrow());
        assertFalse(game.isStuck());
    }

    @Test
    void theBustingSeatStartsTheNextRound() throws NinetyNineException {
        NinetyNine game = aboutToBeStuckA().build();
        game.play(C, c("9H"), null);
        game.play(A, c("JH"), null);

        game.nextRound();

        assertEquals(A, game.currentSeat());
        assertEquals(0, game.total());
    }

    @Test
    void aStuckSeatMayStillNotPlayACardOutsideItsHandOrOutOfTurn() throws NinetyNineException {
        NinetyNine game = aboutToBeStuckA().build();
        game.play(C, c("9H"), null);
        int version = game.version();

        assertThrows(CardNotInHandException.class, () -> game.play(A, c("2S"), null));
        assertThrows(NotPlayersTurnException.class, () -> game.play(B, c("2S"), null));
        assertEquals(version, game.version());
    }

    @Test
    void aStuckAceCountsOneOrElevenAndBothGoOver() throws NinetyNineException {
        NinetyNine one = game(2).total(99).current(A).hand(A, "AH 8H 7H").build();
        NinetyNine eleven = game(2).total(99).current(A).hand(A, "AH 8H 7H").build();

        List<NinetyNineEvent> asOne = one.play(A, c("AH"), 1);
        List<NinetyNineEvent> asEleven = eleven.play(A, c("AH"), 11);

        assertEquals(new Played(A, c("AH"), 1, 100, false), asOne.get(0));
        assertEquals(new Played(A, c("AH"), 11, 110, false), asEleven.get(0));
        assertEquals(Phase.ROUND_OVER, one.phase());
        assertEquals(Phase.ROUND_OVER, eleven.phase());
    }

    @Test
    void aStuckAceStillNeedsAValueOfOneOrEleven() {
        NinetyNine game = game(2).total(99).current(A).hand(A, "AH 8H 7H").build();

        assertThrows(AceValueException.class, () -> game.play(A, c("AH"), null));
        assertThrows(AceValueException.class, () -> game.play(A, c("AH"), 5));
        assertEquals(Phase.PLAYING, game.phase());
    }

    @Test
    void anUnsafeCardIsStillRefusedWhileASafeCardExists() {
        NinetyNine game = game(3).total(95).current(A).hand(A, "9H 7H JH").build();

        assertThrows(UnsafeCardException.class, () -> game.play(A, c("7H"), null));
    }

    @Test
    void aStuckSeatOnItsLastLifeIsEliminatedByItsPlay() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(A).lives(B, 1).hand(A, "9H 2H 3H").hand(B, "7H 8H JH").build();
        game.play(A, c("9H"), null);
        assertEquals(B, game.currentSeat());
        assertEquals(Phase.PLAYING, game.phase());

        List<NinetyNineEvent> events = game.play(B, c("8H"), null);

        assertEquals(new Played(B, c("8H"), null, 103, false), events.get(0));
        assertEquals(new Busted(B, 103, cards("7H JH"), 0), events.get(1));
        assertEquals(new Eliminated(B, 3), events.get(2));
        assertEquals(List.of(A, C), game.liveSeats());
        assertEquals(Phase.ROUND_OVER, game.phase());
    }

    @Test
    void aForfeitMovingTheTurnToAStuckSeatLeavesItOnTurn() throws NinetyNineException {
        NinetyNine game = game(3).total(95).hand(A, "9H 2H 3H").hand(B, "7H 8H JH").build();

        game.forfeit(A);

        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(B, game.currentSeat());
        assertTrue(game.lastBust().isEmpty());
        assertTrue(game.isStuck());
        game.play(B, c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(B, game.lastBust().orElseThrow().seat());
    }

    @Test
    void aStuckSeatThatForfeitsIsEliminatedAndTheTurnMovesOn() {
        NinetyNine game = game(3).total(95).current(A).hand(A, "7H 8H JH").hand(B, "2S 3S 4S").build();
        assertTrue(game.isStuck());

        game.forfeit(A);

        assertEquals(B, game.currentSeat());
        assertEquals(Phase.PLAYING, game.phase());
    }
}
