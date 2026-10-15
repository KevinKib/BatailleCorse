package org.kevinkib.cardgames.mangola.domain;

import java.util.random.RandomGenerator;

/**
 * Test-only full boards (32 seeds per side): the classic checkerboard and a random layout. The game itself
 * always starts in free placement (owner decision 2026-10-15); these boards only feed the test seams that
 * need a position ({@code Mangola.fromLayout}) and the bot tests.
 */
public final class MangolaLayouts {

    private MangolaLayouts() {
    }

    /**
     * The classic opening of the JS game: 4 seeds in every pit whose column index (a = 0) plus row number
     * (1 to 4) is even, and nothing elsewhere; 32 seeds on each side.
     */
    public static MangolaBoard checkerboard() {
        int[] seeds = new int[MangolaGeometry.PITS];
        for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
            int column = pit % MangolaGeometry.COLUMNS;
            int row = pit / MangolaGeometry.COLUMNS + 1;
            seeds[pit] = (column + row) % 2 == 0 ? 4 : 0;
        }
        return MangolaBoard.of(seeds);
    }

    /**
     * 32 seeds dropped one by one on a uniformly drawn pit of the side, side A then side B. Unlike the JS,
     * the sixteenth pit of each loop can be drawn.
     */
    public static MangolaBoard random(RandomGenerator random) {
        MangolaBoard board = MangolaBoard.empty();
        for (Side side : Side.values()) {
            int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
            for (int seed = 0; seed < Mangola.SEEDS_PER_SIDE; seed++) {
                counts[random.nextInt(MangolaGeometry.LOOP_LENGTH)]++;
            }
            board = board.withLayout(side, counts);
        }
        return board;
    }
}
