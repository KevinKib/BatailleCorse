package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.MangolaRules.Endless;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Endless sowings: found by the spec's simulation, detected exactly, and a rule switch for what they mean. */
class EndlessSowingTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    /** The spec's 13-seed fixture: sowing c1 cycles for ever on the mover's own side. */
    static final String ENDLESS_SIDE_A = "a1:1 c1:2 d1:1 e1:2 g1:1 b2:1 d2:1 f2:1 g2:2 h2:1";

    private static MangolaBoard endlessBoard() {
        return Boards.of(ENDLESS_SIDE_A);
    }

    @Test
    void theSpecFixtureIsEndlessAndFoundByTheCycleCheckNotByTheCap() {
        SowingResult result = Sowing.sow(endlessBoard(), Side.A, Boards.pit("c1"), MangolaRules.OWNER);
        assertTrue(result.endless());
        assertTrue(result.drops() < 10_000, "found by the cycle check, far below the cap: " + result.drops());
        assertEquals(endlessBoard(), result.board(), "an endless sowing leaves the board unchanged");
        assertEquals(List.of(), result.trace());
    }

    @Test
    void theSpecFixtureCyclesAfterAbout120LapsAnd272Seeds() {
        // The cycle is entered at once (a pure cycle of 272 seeds dropped), so a cap just above one
        // period must not find it endless by the cap alone while a cap below must.
        assertTrue(Sowing.sow(endlessBoard(), Side.A, Boards.pit("c1"), MangolaRules.OWNER, 100).endless());
        SowingResult result = Sowing.sow(endlessBoard(), Side.A, Boards.pit("c1"), MangolaRules.OWNER);
        assertTrue(result.drops() >= 272, "at least one full period is dropped: " + result.drops());
    }

    @Test
    void theCapStopsASyntheticLongSowing() {
        // E8 is finite (33 seeds dropped); with a cap of 20 it is treated as endless.
        SowingResult capped = Sowing.sow(Boards.of("a2:17"), Side.A, Boards.pit("a2"), MangolaRules.OWNER, 20);
        assertTrue(capped.endless());
        assertFalse(Sowing.sow(Boards.of("a2:17"), Side.A, Boards.pit("a2"), MangolaRules.OWNER, 33).endless());
    }

    @Test
    void theDefaultCapIsTheJavaScriptOne() {
        assertEquals(100_000, Sowing.MAX_DROPS);
    }

    @Test
    void anEndlessPitIsNotAMoveUnderIllegalAndIsLeftOutOfLegalPits() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, endlessBoard());
        List<Integer> legal = game.legalPits(SEAT_0);
        assertFalse(legal.contains(Boards.pit("c1")));
        // The other pits with two seeds or more are not endless here.
        assertTrue(legal.containsAll(List.of(Boards.pit("e1"), Boards.pit("g2"))), legal.toString());
        assertThrows(EndlessSowingException.class, () -> game.sow(SEAT_0, Boards.pit("c1")));
        assertEquals(0, game.version(), "a refused move changes nothing");
        assertEquals(SEAT_0, game.currentSeat());
    }

    @Test
    void anEndlessPitIsPlayedUnderDrawAndEndsTheGameWithNoWinner() throws Exception {
        MangolaRules draw = MangolaRules.OWNER.withEndless(Endless.DRAW);
        Mangola game = Mangola.fromLayout(GameId.generate(), draw, SEAT_0, endlessBoard());
        assertTrue(game.legalPits(SEAT_0).contains(Boards.pit("c1")));
        game.sow(SEAT_0, Boards.pit("c1"));
        assertTrue(game.isFinished());
        assertNull(game.result().winner());
        assertEquals(MangolaResult.Reason.ENDLESS_SOWING, game.result().reason());
        assertTrue(game.result().isDraw());
        assertEquals(1, game.version());
    }

    /** Found by a random search: the only pit with two seeds or more (h2) is endless. */
    static final String ONLY_ENDLESS_SIDE_A = "b1:1 d1:1 f1:1 h1:1 a2:1 c2:1 e2:1 g2:1 h2:3";

    @Test
    void aPlayerWhoseOnlyPitIsEndlessHasNoLegalMoveAndLosesRightAfterTheOpponentsMove() throws Exception {
        MangolaBoard sideB = mirror(Boards.of(ONLY_ENDLESS_SIDE_A));
        MangolaBoard board = sideB.with(Boards.pit("a2"), 3);
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, board);
        assertEquals(List.of(), game.legalPits(SEAT_1), "not offered outside its turn either way");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertTrue(game.isFinished());
        assertEquals(SEAT_0, game.result().winner());
        assertEquals(MangolaResult.Reason.NO_MOVE, game.result().reason());
    }

    @Test
    void underDrawTheSamePlayerStillHasTheEndlessMoveAndTheGameGoesOn() throws Exception {
        MangolaRules draw = MangolaRules.OWNER.withEndless(Endless.DRAW);
        MangolaBoard board = mirror(Boards.of(ONLY_ENDLESS_SIDE_A)).with(Boards.pit("a2"), 3);
        Mangola game = Mangola.fromLayout(GameId.generate(), draw, SEAT_0, board);
        game.sow(SEAT_0, Boards.pit("a2"));
        assertFalse(game.isFinished());
        assertEquals(List.of(Boards.pit("h4")), game.legalPits(SEAT_1));
    }

    @Test
    void legalPitsOnTheWorstFixtureRunsUnder50Milliseconds() {
        // Both sides carry an endless pattern: every candidate pit costs a full cycle detection.
        MangolaBoard both = mirror(endlessBoard());
        for (int pit : MangolaGeometry.loop(Side.A)) {
            both = both.with(pit, endlessBoard().seeds(pit));
        }
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, both);
        game.legalPits(SEAT_0); // warm up
        long start = System.nanoTime();
        game.legalPits(SEAT_0);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 50, "legalPits took " + millis + " ms");
    }

    /** The side-A pattern rotated onto side B (same loop positions). */
    static MangolaBoard mirror(MangolaBoard sideA) {
        int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
        List<Integer> loopA = MangolaGeometry.loop(Side.A);
        for (int i = 0; i < counts.length; i++) {
            counts[i] = sideA.seeds(loopA.get(i));
        }
        return MangolaBoard.empty().withLayout(Side.B, counts);
    }
}
