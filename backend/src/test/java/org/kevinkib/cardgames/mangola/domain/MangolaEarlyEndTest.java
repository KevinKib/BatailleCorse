package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.MangolaRules.Endless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The earliest provable end: a player left with no pit of two seeds or more right after their own move can
 * never move again (seeds only reach a side by its owner's sowing, and a capture only takes seeds away), so
 * the opponent, who still has a move, wins at once instead of one move later.
 */
class MangolaEarlyEndTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private static Mangola game(String sideA, String sideB) {
        return Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, Boards.of(sideA, sideB));
    }

    @Test
    void aMoverLeftWithOnlySingleSeedsLosesRightAway() throws Exception {
        // a2:3 sows b2, c2 and, d2 being empty, e2: side A is left with c1 b2 c2 e2 at one seed each.
        Mangola game = game("a2:3 c1:1", "c3:2 d4:3");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertTrue(game.isFinished());
        assertEquals(SEAT_1, game.result().winner());
        assertEquals(MangolaResult.Reason.CANNOT_MOVE_AGAIN, game.result().reason());
        assertFalse(game.result().isDraw());
        assertEquals(1, game.version());
    }

    @Test
    void sideBMoverIsJudgedTheSameWay() throws Exception {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0,
                Boards.of("a2:2 h1:1", "c3:3"), SEAT_1);
        game.sow(SEAT_1, Boards.pit("c3"));
        assertEquals(SEAT_0, game.result().winner());
        assertEquals(MangolaResult.Reason.CANNOT_MOVE_AGAIN, game.result().reason());
    }

    @Test
    void aMoverStillHoldingAPitOfTwoSeedsKeepsPlaying() throws Exception {
        Mangola game = game("a2:3 h1:2", "c3:2 d4:3");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertFalse(game.isFinished());
        assertEquals(SEAT_1, game.currentSeat());
    }

    @Test
    void anOpponentWithoutAMoveStillLosesOnTheExistingRuleFirst() throws Exception {
        // Both sides end with single seeds: the opponent is the one to move and has none, so the mover wins.
        Mangola game = game("a2:3", "c3:1 d4:1");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertEquals(SEAT_0, game.result().winner());
        assertEquals(MangolaResult.Reason.NO_MOVE, game.result().reason());
    }

    @Test
    void whenEndlessSowingsAreADrawTheOpponentMightStillDrawSoNothingIsProven() throws Exception {
        // Side B's only pit of two seeds or more is endless: with DRAW rules it could be played for a draw.
        MangolaBoard sideB = EndlessSowingTest.mirror(Boards.of(EndlessSowingTest.ONLY_ENDLESS_SIDE_A));
        MangolaBoard board = sideB.with(Boards.pit("a2"), 3);
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER.withEndless(Endless.DRAW),
                SEAT_0, board);
        game.sow(SEAT_0, Boards.pit("a2"));
        assertFalse(game.isFinished());
    }
}
