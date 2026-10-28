package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.c;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

/** The last card played is public state (the table shows it and a reconnecting seat needs it). */
class NinetyNineLastPlayTest {

    @Test
    void thereIsNoLastPlayBeforeTheFirstCard() {
        assertTrue(game(2).build().lastPlay().isEmpty());
    }

    @Test
    void aPlayRecordsTheSeatTheCardTheAceValueAndTheTotalsBeforeAndAfter() throws NinetyNineException {
        NinetyNine game = game(2).total(40).hand(A, "AS 9H 9D").build();

        game.play(A, c("AS"), 11);

        LastPlay last = game.lastPlay().orElseThrow();
        assertEquals(A, last.seat());
        assertEquals(c("AS"), last.card());
        assertEquals(11, last.aceValue());
        assertEquals(40, last.totalBefore());
        assertEquals(51, last.totalAfter());
        assertFalse(last.reversed());
    }

    @Test
    void aCardWithoutAValueRecordsNullAndAFourRecordsTheReversal() throws NinetyNineException {
        NinetyNine game = game(3).total(30).hand(A, "4S 9H 9D").build();

        game.play(A, c("4S"), null);

        LastPlay last = game.lastPlay().orElseThrow();
        assertEquals(null, last.aceValue());
        assertEquals(30, last.totalBefore());
        assertEquals(30, last.totalAfter());
        assertTrue(last.reversed());
    }

    @Test
    void aBustKeepsTheLastPlayUntilTheNextDealClearsIt() throws NinetyNineException {
        NinetyNine game = game(2).hand(A, "KS 9H 9D").hand(B, "7H 8H 2H").build();

        game.play(A, c("KS"), null);
        game.play(B, c("7H"), null);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(c("7H"), game.lastPlay().orElseThrow().card());
        assertEquals(106, game.lastPlay().orElseThrow().totalAfter());

        game.nextRound();

        assertTrue(game.lastPlay().isEmpty());
    }
}
