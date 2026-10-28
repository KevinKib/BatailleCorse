package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;
import org.kevinkib.cardgames.ninetynine.domain.event.StockReshuffled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.C;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.c;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.cards;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

/** Spec section 6.2, examples 1 to 6 and 9, plus the play pipeline (refusals never mutate). */
class NinetyNinePlayTest {

    @Test
    void example1_kingSetsNinetyNineAndANineKeepsIt() throws NinetyNineException {
        NinetyNine game = game(3).total(90).hand(A, "KH 2H 3H").hand(B, "9H 5H 6H").build();

        game.play(A, c("KH"), null);
        assertEquals(99, game.total());
        assertEquals(B, game.currentSeat());

        game.play(B, c("9H"), null);
        assertEquals(99, game.total());
        assertEquals(C, game.currentSeat());
    }

    @Test
    void example3_aceElevenIsRefusedWhenItGoesOver() throws NinetyNineException {
        NinetyNine game = game(3).total(89).hand(A, "AH 2H 4H").build();
        int version = game.version();

        assertThrows(AceValueException.class, () -> game.play(A, c("AH"), 11));
        assertThrows(AceValueException.class, () -> game.play(A, c("AH"), null));
        assertThrows(AceValueException.class, () -> game.play(A, c("AH"), 5));
        assertEquals(version, game.version());
        assertEquals(89, game.total());
        assertEquals(cards("AH 2H 4H"), game.hand(A));

        game.play(A, c("AH"), 1);
        assertEquals(90, game.total());
    }

    @Test
    void aceElevenIsAllowedWhenItStaysAtNinetyNine() throws NinetyNineException {
        NinetyNine game = game(3).total(88).hand(A, "AH 2H 4H").build();

        List<NinetyNineEvent> events = game.play(A, c("AH"), 11);

        assertEquals(99, game.total());
        assertEquals(new Played(A, c("AH"), 11, 99, false), events.get(0));
    }

    @Test
    void aceValueIsIgnoredForOtherCards() throws NinetyNineException {
        NinetyNine game = game(3).total(10).hand(A, "2H 3H 4H").build();

        Played played = (Played) game.play(A, c("2H"), 11).get(0);

        assertEquals(12, game.total());
        assertNull(played.aceValue());
    }

    @Test
    void example4_anUnsafeCardIsRefusedWhileASafeOneExistsAndTheFourReverses() throws NinetyNineException {
        NinetyNine game = game(3).total(95).hand(A, "8H 4H JH").build();
        int version = game.version();

        assertThrows(UnsafeCardException.class, () -> game.play(A, c("8H"), null));
        assertThrows(UnsafeCardException.class, () -> game.play(A, c("JH"), null));
        assertEquals(version, game.version());

        Played played = (Played) game.play(A, c("4H"), null).get(0);

        assertEquals(95, game.total());
        assertTrue(played.reversed());
        assertEquals(Direction.COUNTER_CLOCKWISE, game.direction());
        assertEquals(C, game.currentSeat());
    }

    @Test
    void anAceThatCanOnlyGoOverIsUnsafeWhileASafeCardExists() {
        NinetyNine game = game(3).total(99).hand(A, "AH 9H 4H").build();

        assertThrows(UnsafeCardException.class, () -> game.play(A, c("AH"), 1));
    }

    @Test
    void example5_aTenStopsAtZero() throws NinetyNineException {
        NinetyNine game = game(3).total(6).hand(A, "10H 2H 3H").build();

        game.play(A, c("10H"), null);

        assertEquals(0, game.total());
    }

    @Test
    void aTenRemovesTen() throws NinetyNineException {
        NinetyNine game = game(3).total(60).hand(A, "10H 2H 3H").build();

        game.play(A, c("10H"), null);

        assertEquals(50, game.total());
    }

    @Test
    void example6_withTwoPlayersAReversalLeavesTheOtherPlayerNext() throws NinetyNineException {
        NinetyNine game = game(2).total(10).hand(A, "4H 2H 3H").build();

        game.play(A, c("4H"), null);

        assertEquals(Direction.COUNTER_CLOCKWISE, game.direction());
        assertEquals(B, game.currentSeat());
    }

    @Test
    void reverseDirectionSendsTheTurnBackwardsAndAReversalComesBack() throws NinetyNineException {
        NinetyNine game = game(3).direction(Direction.COUNTER_CLOCKWISE).hand(A, "2H 3H 4H")
                .hand(C, "4H 2H 3H").current(A).build();

        game.play(A, c("2H"), null);
        assertEquals(C, game.currentSeat());

        game.play(C, c("4H"), null);
        assertEquals(Direction.CLOCKWISE, game.direction());
        assertEquals(A, game.currentSeat());
    }

    @Test
    void aPlayMovesTheCardToThePileAndDrawsTheTopOfTheStock() throws NinetyNineException {
        NinetyNine game = game(3).hand(A, "2H 3H 4H").stock("KD QD JD").build();

        game.play(A, c("2H"), null);

        assertEquals(cards("3H 4H KD"), game.hand(A));
        assertEquals(c("2H"), game.pileTop().orElseThrow());
        assertEquals(cards("2H"), game.pileSinceReshuffle());
        assertEquals(2, game.stockSize());
        assertEquals(3, game.handSizes().get(A));
    }

    @Test
    void eachAcceptedPlayBumpsTheVersionOnce() throws NinetyNineException {
        NinetyNine game = game(3).hand(A, "2H 3H 4H").build();
        int before = game.version();

        game.play(A, c("2H"), null);

        assertEquals(before + 1, game.version());
    }

    @Test
    void aRefusedPlayNeverMutates() {
        NinetyNine game = game(3).total(50).hand(A, "2H 3H 4H").build();
        int version = game.version();

        assertThrows(NotPlayersTurnException.class, () -> game.play(B, c("9H"), null));
        assertThrows(CardNotInHandException.class, () -> game.play(A, c("KH"), null));

        assertEquals(version, game.version());
        assertEquals(50, game.total());
        assertEquals(A, game.currentSeat());
        assertEquals(cards("2H 3H 4H"), game.hand(A));
        assertEquals(0, game.pileSinceReshuffle().size());
    }

    @Test
    void anEliminatedSeatIsSkipped() throws NinetyNineException {
        NinetyNine game = game(3).lives(B, 0).hand(A, "2H 3H 4H").build();

        game.play(A, c("2H"), null);

        assertEquals(C, game.currentSeat());
    }

    @Test
    void example9_anEmptyStockIsRebuiltFromThePileKeepingTheTop() throws NinetyNineException {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        NinetyNine game = game(3).deckSource(source).hand(A, "2H 3H 4H").stock("")
                .pile("5C 6C 7C 8C").build();
        source.fixReshuffle(game.getId(), cards("8C 5C 7C 6C"));

        List<NinetyNineEvent> events = game.play(A, c("2H"), null);

        assertEquals(new StockReshuffled(4), events.get(1));
        assertEquals(cards("3H 4H 8C"), game.hand(A));
        assertEquals(3, game.stockSize());
        assertEquals(cards("2H"), game.pileSinceReshuffle());
        assertEquals(c("2H"), game.pileTop().orElseThrow());
    }

    @Test
    void aSeatThatHoldsASafeCardIsNeverBustedAndAStuckSeatIsReportedWithItsRemainingHand() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();

        List<NinetyNineEvent> events = game.play(C, c("9H"), null);
        assertEquals(1, events.size());
        events = game.play(A, c("7H"), null);

        Busted busted = (Busted) events.get(1);
        assertEquals(A, busted.seat());
        assertEquals(102, busted.totalBefore());
        assertEquals(cards("8H JH"), busted.hand());
        assertEquals(2, busted.livesLeft());
    }

    @Test
    void nothingIsAcceptedOutsideThePlayingPhase() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());

        assertThrows(WrongPhaseException.class, () -> game.play(A, c("8H"), null));
    }
}
