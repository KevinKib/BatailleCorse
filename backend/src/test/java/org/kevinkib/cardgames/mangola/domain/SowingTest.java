package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Capture;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Drop;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Pick;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Relay;
import org.kevinkib.cardgames.mangola.domain.SowingStep.ResumeAfter;
import org.kevinkib.cardgames.mangola.domain.SowingStep.SkipEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ten hand-checked examples of the spec (E1 to E10) are the contract of the engine: each asserts
 * the final board and the trace, step by step.
 */
class SowingTest {

    private static Pick pick(String pit, int count) {
        return new Pick(Boards.pit(pit), count);
    }

    private static Drop drop(String pit) {
        return new Drop(Boards.pit(pit));
    }

    private static SkipEmpty skip(String pit) {
        return new SkipEmpty(Boards.pit(pit));
    }

    private static Relay relay(String pit, int count) {
        return new Relay(Boards.pit(pit), count);
    }

    private static Capture capture(String from, String facing, String behind, int count) {
        return new Capture(Boards.pit(from), Boards.pit(facing), Boards.pit(behind), count);
    }

    private static ResumeAfter resume(String pit) {
        return new ResumeAfter(Boards.pit(pit));
    }

    private static SowingResult sow(MangolaBoard board, Side side, String pit) {
        return Sowing.sow(board, side, Boards.pit(pit), MangolaRules.OWNER);
    }

    private static void assertSown(SowingResult result, MangolaBoard expected, SowingStep... trace) {
        assertFalse(result.endless());
        assertEquals(expected, result.board());
        assertEquals(List.of(trace), result.trace());
        assertEquals(64, result.board().total());
    }

    @Test
    void e1_theLastSeedOfAnEmptyNextPitGoesOneFurtherAndTheTurnEnds() {
        SowingResult result = sow(Boards.of("a2:3"), Side.A, "a2");
        assertSown(result, Boards.of("b2:1 c2:1 e2:1"),
                pick("a2", 3), drop("b2"), drop("c2"), skip("d2"), drop("e2"));
    }

    @Test
    void e2_aRelayFromAnOccupiedPitThenTheEmptyPitRule() {
        SowingResult result = sow(Boards.of("a2:2 c2:3"), Side.A, "a2");
        assertSown(result, Boards.of("b2:1 d2:1 e2:1 f2:1 h2:1"),
                pick("a2", 2), drop("b2"), drop("c2"), relay("c2", 4),
                drop("d2"), drop("e2"), drop("f2"), skip("g2"), drop("h2"));
    }

    @Test
    void e3_aCaptureNeedsBothTheFacingPitAndTheOneBehindIt() {
        SowingResult result = sow(Boards.of("a2:2 c2:1", "c3:2 c4:3"), Side.A, "a2");
        assertSown(result, Boards.of("b2:2 c2:3 d2:1 e2:1 g2:1"),
                pick("a2", 2), drop("b2"), drop("c2"), capture("c2", "c3", "c4", 5), resume("a2"),
                drop("b2"), drop("c2"), drop("d2"), drop("e2"), skip("f2"), drop("g2"));
        assertEquals(8, result.board().sideTotal(Side.A));
        assertEquals(0, result.board().sideTotal(Side.B));
    }

    @Test
    void e4_withoutTheBehindPitTheSameSowingRelaysInstead() {
        SowingResult result = sow(Boards.of("a2:2 c2:1", "c3:2"), Side.A, "a2");
        assertSown(result, Boards.of("b2:1 d2:1 f2:1", "c3:2"),
                pick("a2", 2), drop("b2"), drop("c2"), relay("c2", 2),
                drop("d2"), skip("e2"), drop("f2"));
    }

    @Test
    void e5_theOuterRowNeverCaptures() {
        SowingResult result = sow(Boards.of("g1:2 e1:1", "e3:2 e4:2"), Side.A, "g1");
        assertSown(result, Boards.of("b1:1 d1:1 f1:1", "e3:2 e4:2"),
                pick("g1", 2), drop("f1"), drop("e1"), relay("e1", 2),
                drop("d1"), skip("c1"), drop("b1"));
    }

    @Test
    void e6_theLoopTurnsFromRow2ToRow1() {
        SowingResult result = sow(Boards.of("g2:3 a1:4"), Side.A, "g2");
        assertSown(result, Boards.of("a1:4 f1:1 h1:1 h2:1"),
                pick("g2", 3), drop("h2"), drop("h1"), skip("g1"), drop("f1"));
    }

    @Test
    void e7_sideBSowsAlongItsOwnLoopAndCapturesTowardsRow2() {
        SowingResult result = sow(Boards.of("g2:1 g1:1", "h4:2 g3:1"), Side.B, "h4");
        assertSown(result, Boards.of("", "c3:1 e3:1 f3:1 h3:2"),
                pick("h4", 2), drop("h3"), drop("g3"), capture("g3", "g2", "g1", 2), resume("h4"),
                drop("h3"), drop("g3"), relay("g3", 3),
                drop("f3"), drop("e3"), skip("d3"), drop("c3"));
        assertEquals(0, result.board().sideTotal(Side.A));
    }

    @Test
    void e8_aLapOfMoreThan16SeedsDropsIntoItsOwnStartPit() {
        SowingResult result = sow(Boards.of("a2:17"), Side.A, "a2");
        assertFalse(result.endless());
        assertEquals(Boards.of("b1:2 d1:2 f1:2 h1:2 a2:2 c2:3 e2:2 g2:2"), result.board());
        assertEquals(33, result.trace().stream().filter(step -> step instanceof Drop).count());
        assertEquals(drop("a2"), result.trace().get(16));
        assertEquals(drop("b2"), result.trace().get(17));
        assertEquals(relay("b2", 2), result.trace().get(18));
        assertEquals(skip("b2"), result.trace().get(result.trace().size() - 2));
        assertEquals(drop("c2"), result.trace().get(result.trace().size() - 1));
        assertEquals(64, result.board().total());
    }

    @Test
    void e9_twoCapturesInOneMoveResumeFromTheSameLapStart() {
        SowingResult result = sow(Boards.of("a2:2 c2:1 d2:1", "c3:1 c4:2 d3:1 d4:1"), Side.A, "a2");
        assertSown(result, Boards.of("b2:3 d2:3 e2:1 f2:1 h2:1"),
                pick("a2", 2), drop("b2"), drop("c2"), capture("c2", "c3", "c4", 3), resume("a2"),
                drop("b2"), drop("c2"), drop("d2"), capture("d2", "d3", "d4", 2), resume("a2"),
                drop("b2"), drop("c2"), relay("c2", 4),
                drop("d2"), drop("e2"), drop("f2"), skip("g2"), drop("h2"));
        assertEquals(0, result.board().sideTotal(Side.B));
    }

    @Test
    void e10_theEmptyPitRuleNeverRelaysEvenWhenTheLandingPitIsOccupied() {
        SowingResult result = sow(Boards.of("a2:2 d2:5"), Side.A, "a2");
        assertSown(result, Boards.of("b2:1 d2:6"),
                pick("a2", 2), drop("b2"), skip("c2"), drop("d2"));
    }

    @Test
    void theEmptyPitRuleNeverCapturesEither() {
        // d2 is empty, so the last seed goes to e2 and the turn ends: no relay, no capture, even
        // though e2 faces two occupied pits.
        SowingResult result = sow(Boards.of("a2:3", "d3:1 d4:1 e3:1 e4:1"), Side.A, "a2");
        assertSown(result, Boards.of("b2:1 c2:1 e2:1", "d3:1 d4:1 e3:1 e4:1"),
                pick("a2", 3), drop("b2"), drop("c2"), skip("d2"), drop("e2"));
    }

    @Test
    void sowingRefusesAPitOfTheOtherSideOrWithTooFewSeeds() {
        MangolaBoard board = Boards.of("a2:3 b2:1", "c3:2");
        assertThrows(IllegalArgumentException.class, () -> sow(board, Side.A, "c3"));
        assertThrows(IllegalArgumentException.class, () -> sow(board, Side.A, "b2"));
        assertThrows(IllegalArgumentException.class, () -> sow(board, Side.A, "h1"));
    }

    @Test
    void theInputBoardIsNeverModified() {
        MangolaBoard board = Boards.of("a2:2 c2:1", "c3:2 c4:3");
        sow(board, Side.A, "a2");
        assertEquals(Boards.of("a2:2 c2:1", "c3:2 c4:3"), board);
    }

    // ---------------------------------------------------------------- invariants

    private static MangolaBoard randomBoard(Random random) {
        int[] counts = new int[MangolaGeometry.PITS];
        for (Side side : Side.values()) {
            List<Integer> loop = MangolaGeometry.loop(side);
            for (int seed = 0; seed < 32; seed++) {
                counts[loop.get(random.nextInt(16))]++;
            }
        }
        return MangolaBoard.of(counts);
    }

    /** Applies a trace to a board with no knowledge of the rules: the frontend animation relies on it. */
    private static MangolaBoard replay(MangolaBoard start, List<SowingStep> trace) {
        int[] seeds = start.toArray();
        for (SowingStep step : trace) {
            if (step instanceof Pick pick) {
                seeds[pick.pit()] -= pick.count();
            } else if (step instanceof Relay relay) {
                seeds[relay.pit()] -= relay.count();
            } else if (step instanceof Drop drop) {
                seeds[drop.pit()]++;
            } else if (step instanceof Capture capture) {
                assertEquals(capture.count(), seeds[capture.facing()] + seeds[capture.behind()]);
                seeds[capture.facing()] = 0;
                seeds[capture.behind()] = 0;
            } else if (step instanceof SkipEmpty skip) {
                assertEquals(0, seeds[skip.pit()]);
            }
        }
        return MangolaBoard.of(seeds);
    }

    @Test
    void propertiesHoldOnRandomBoards() {
        Random random = new Random(20261015L);
        int sown = 0;
        for (int game = 0; game < 300; game++) {
            MangolaBoard board = randomBoard(random);
            for (Side side : Side.values()) {
                for (int pit : MangolaGeometry.loop(side)) {
                    if (board.seeds(pit) < 2) {
                        continue;
                    }
                    SowingResult result = Sowing.sow(board, side, pit, MangolaRules.OWNER);
                    if (result.endless()) {
                        continue;
                    }
                    sown++;
                    Side other = side == Side.A ? Side.B : Side.A;
                    assertEquals(64, result.board().total());
                    assertTrue(result.board().sideTotal(side) >= board.sideTotal(side));
                    assertTrue(result.board().sideTotal(other) <= board.sideTotal(other));
                    for (int p : MangolaGeometry.loop(other)) {
                        assertTrue(result.board().seeds(p) <= board.seeds(p), "the opponent never gains");
                    }
                    assertEquals(result.board(), replay(board, result.trace()));
                }
            }
        }
        assertTrue(sown > 1000);
    }

    @Test
    void theTraceAlwaysStartsWithAPickAndEndsWithADrop() {
        Random random = new Random(7L);
        for (int game = 0; game < 50; game++) {
            MangolaBoard board = randomBoard(random);
            int pit = MangolaGeometry.loop(Side.A).stream().filter(p -> board.seeds(p) >= 2).findFirst().orElseThrow();
            SowingResult result = Sowing.sow(board, Side.A, pit, MangolaRules.OWNER);
            if (result.endless()) {
                continue;
            }
            List<SowingStep> trace = new ArrayList<>(result.trace());
            assertTrue(trace.get(0) instanceof Pick);
            assertTrue(trace.get(trace.size() - 1) instanceof Drop);
        }
    }
}
