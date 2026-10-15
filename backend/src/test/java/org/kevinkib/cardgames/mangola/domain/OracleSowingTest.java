package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential test: {@link Sowing} against a naive transcription of the move algorithm written from the
 * spec's wording with its own name-based tables, no trace and no cycle detection (it only has the
 * 100,000-drop cap of the JS). It stands in for a characterisation against {@code ngola.js} (see the PR
 * description): same rules, independent code. A cycle found by Brent must be a sowing the naive oracle
 * never finishes.
 */
class OracleSowingTest {

    private static final int CAP = Sowing.MAX_DROPS;

    private static final String[] LOOP_A = "a2 b2 c2 d2 e2 f2 g2 h2 h1 g1 f1 e1 d1 c1 b1 a1".split(" ");
    private static final String[] LOOP_B = "a4 b4 c4 d4 e4 f4 g4 h4 h3 g3 f3 e3 d3 c3 b3 a3".split(" ");

    private static final Map<String, String> NEXT = new HashMap<>();

    static {
        for (String[] loop : new String[][] {LOOP_A, LOOP_B}) {
            for (int i = 0; i < loop.length; i++) {
                NEXT.put(loop[i], loop[(i + 1) % loop.length]);
            }
        }
    }

    private static boolean inner(String pit) {
        return pit.charAt(1) == '2' || pit.charAt(1) == '3';
    }

    private static String facing(String pit) {
        return "" + pit.charAt(0) + (pit.charAt(1) == '2' ? '3' : '2');
    }

    private static String behind(String pit) {
        return "" + pit.charAt(0) + (pit.charAt(1) == '2' ? '4' : '1');
    }

    /** Returns the resulting counts by name, or null when the sowing is still going after the cap. */
    private static Map<String, Integer> naive(Map<String, Integer> start, String pit) {
        Map<String, Integer> b = new HashMap<>(start);
        int hand = b.get(pit);
        b.put(pit, 0);
        String lapStart = pit;
        String at = pit;
        int dropped = 0;
        while (true) {
            while (hand > 1) {
                at = NEXT.get(at);
                b.merge(at, 1, Integer::sum);
                hand--;
                if (++dropped > CAP) {
                    return null;
                }
            }
            String next = NEXT.get(at);
            if (b.getOrDefault(next, 0) == 0) {
                b.merge(NEXT.get(next), 1, Integer::sum);
                return b;
            }
            b.merge(next, 1, Integer::sum);
            at = next;
            if (inner(next) && b.getOrDefault(facing(next), 0) > 0 && b.getOrDefault(behind(next), 0) > 0) {
                hand = b.get(facing(next)) + b.get(behind(next));
                b.put(facing(next), 0);
                b.put(behind(next), 0);
                at = lapStart;
            } else {
                hand = b.get(next);
                b.put(next, 0);
                lapStart = next;
            }
            if (++dropped > CAP) {
                return null;
            }
        }
    }

    private static Map<String, Integer> byName(MangolaBoard board) {
        Map<String, Integer> map = new HashMap<>();
        for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
            map.put(MangolaGeometry.name(pit), board.seeds(pit));
        }
        return map;
    }

    private static void compare(MangolaBoard board, Side side, int pit, int[] counters) {
        SowingResult result = Sowing.sow(board, side, pit, MangolaRules.OWNER);
        Map<String, Integer> expected = naive(byName(board), MangolaGeometry.name(pit));
        if (expected == null) {
            assertTrue(result.endless(), "the oracle never finishes: " + board + " pit " + MangolaGeometry.name(pit));
            counters[1]++;
            return;
        }
        assertEquals(false, result.endless(), board + " pit " + MangolaGeometry.name(pit));
        Map<String, Integer> actual = byName(result.board());
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name),
                    name + " on " + board + " after " + MangolaGeometry.name(pit));
        }
        counters[0]++;
    }

    @Test
    void sowingAgreesWithTheNaiveOracleOnRandomFullBoards() {
        Random random = new Random(15102026L);
        int[] counters = new int[2];
        for (int game = 0; game < 400; game++) {
            int[] counts = new int[MangolaGeometry.PITS];
            for (Side side : Side.values()) {
                List<Integer> loop = MangolaGeometry.loop(side);
                for (int seed = 0; seed < 32; seed++) {
                    counts[loop.get(random.nextInt(16))]++;
                }
            }
            MangolaBoard board = MangolaBoard.of(counts);
            for (Side side : Side.values()) {
                for (int pit : MangolaGeometry.loop(side)) {
                    if (board.seeds(pit) >= 2) {
                        compare(board, side, pit, counters);
                    }
                }
            }
        }
        assertTrue(counters[0] > 3000, "finite sowings compared: " + counters[0]);
    }

    @Test
    void sowingAgreesWithTheNaiveOracleOnSparseBoardsWhereEndlessSowingsAppear() {
        Random random = new Random(20261015L);
        int[] counters = new int[2];
        for (int game = 0; game < 3000; game++) {
            int[] counts = new int[MangolaGeometry.PITS];
            List<Integer> loop = MangolaGeometry.loop(Side.A);
            int seeds = 6 + random.nextInt(14);
            for (int seed = 0; seed < seeds; seed++) {
                counts[loop.get(random.nextInt(16))]++;
            }
            MangolaBoard board = MangolaBoard.of(counts);
            for (int pit : loop) {
                if (board.seeds(pit) >= 2) {
                    compare(board, Side.A, pit, counters);
                }
            }
        }
        assertTrue(counters[0] > 1000);
    }

    @Test
    void theKnownEndlessFixturesNeverFinishInTheNaiveOracleEither() {
        String[][] fixtures = {
                {EndlessSowingTest.ENDLESS_SIDE_A, "c1"},
                {EndlessSowingTest.ONLY_ENDLESS_SIDE_A, "h2"},
                {"a1:1 c1:1 e1:1 g1:1 h1:3 b2:1 d2:1 f2:1 h2:1", "h1"},
                {"a1:1 c1:1 d1:3 e1:1 g1:1 b2:1 d2:1 f2:1 h2:1", "d1"},
                {"b1:1 d1:1 f1:1 h1:1 a2:1 c2:1 d2:3 e2:1 g2:1", "d2"},
                {"a1:3 b1:1 d1:1 f1:1 h1:1 a2:1 c2:1 e2:1 g2:1", "a1"},
                {"b1:1 d1:1 f1:1 h1:1 a2:1 b2:3 c2:1 e2:1 g2:1", "b2"}};
        int[] counters = new int[2];
        for (String[] fixture : fixtures) {
            compare(Boards.of(fixture[0]), Side.A, Boards.pit(fixture[1]), counters);
        }
        assertEquals(fixtures.length, counters[1]);
    }
}
