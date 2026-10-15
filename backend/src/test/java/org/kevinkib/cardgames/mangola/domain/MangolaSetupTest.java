package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MangolaSetupTest {

    /** A generator that answers {@code nextInt(bound)} from a script, so a board is known in advance. */
    static RandomGenerator scripted(int... values) {
        return new RandomGenerator() {
            private int next;

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("only nextInt(bound) is scripted");
            }

            @Override
            public int nextInt(int bound) {
                int value = values[next++ % values.length];
                if (value >= bound) {
                    throw new IllegalStateException("scripted " + value + " for bound " + bound);
                }
                return value;
            }
        };
    }

    @Test
    void keysAreStableAndFreeIsTheDefault() {
        assertEquals("free", MangolaSetup.FREE.key());
        assertEquals("checkerboard", MangolaSetup.CHECKERBOARD.key());
        assertEquals("random", MangolaSetup.RANDOM.key());
        assertEquals(MangolaSetup.FREE, MangolaSetup.DEFAULT);
        assertEquals(MangolaSetup.RANDOM, MangolaSetup.fromKey("random"));
        assertThrows(IllegalArgumentException.class, () -> MangolaSetup.fromKey("two-per-pit"));
    }

    /** The JS classic set-up: 4 seeds in every pit whose column letter index plus row number (1 to 4) is even. */
    @Test
    void theCheckerboardIsTheJsClassicBoard() {
        MangolaBoard board = MangolaSetup.checkerboard();
        assertEquals(Boards.of("a2:4 c2:4 e2:4 g2:4 b1:4 d1:4 f1:4 h1:4",
                "a4:4 c4:4 e4:4 g4:4 b3:4 d3:4 f3:4 h3:4"), board);
        assertEquals(32, board.sideTotal(Side.A));
        assertEquals(32, board.sideTotal(Side.B));
    }

    @Test
    void theCheckerboardSetupBuildsThePresetBoard() {
        assertEquals(MangolaSetup.checkerboard(), MangolaSetup.CHECKERBOARD.board(scripted(0)));
    }

    @Test
    void theFreeSetupHasNoPresetBoard() {
        assertThrows(IllegalStateException.class, () -> MangolaSetup.FREE.board(scripted(0)));
    }

    @Test
    void theRandomBoardDrops32SeedsPerSideUniformlyOverTheSixteenPits() {
        // 0..15 twice for each side: two seeds in every pit
        int[] script = new int[64];
        for (int i = 0; i < 64; i++) {
            script[i] = i % 16;
        }
        MangolaBoard board = MangolaSetup.random(scripted(script));
        for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
            assertEquals(2, board.seeds(pit), MangolaGeometry.name(pit));
        }
    }

    @Test
    void theRandomBoardCanFillTheSixteenthPitOfEachLoopUnlikeTheJs() {
        // the JS drew Math.floor(random * 15): never the last pit of the loop (h2 for A, h4 for B in its text)
        MangolaBoard board = MangolaSetup.random(scripted(15));
        assertEquals(32, board.seeds(MangolaGeometry.loop(Side.A).get(15)));
        assertEquals(32, board.seeds(MangolaGeometry.loop(Side.B).get(15)));
        assertEquals(64, board.total());
    }

    @Test
    void theRandomBoardFillsSideAFirstThenSideB() {
        // 32 drops on loop index 0 for side A, then 32 drops on loop index 3 for side B
        int[] script = new int[64];
        for (int i = 32; i < 64; i++) {
            script[i] = 3;
        }
        MangolaBoard board = MangolaSetup.random(scripted(script));
        assertEquals(32, board.seeds(MangolaGeometry.loop(Side.A).get(0)));
        assertEquals(32, board.seeds(MangolaGeometry.loop(Side.B).get(3)));
    }

    @Test
    void aSeededGeneratorAlwaysGivesTwoBalancedHalves() {
        for (long seed = 0; seed < 50; seed++) {
            MangolaBoard board = MangolaSetup.random(new java.util.Random(seed));
            assertEquals(32, board.sideTotal(Side.A));
            assertEquals(32, board.sideTotal(Side.B));
        }
    }
}
