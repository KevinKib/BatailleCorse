package org.kevinkib.cardgames.mangola.domain;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * How the 32 seeds of each side get on the board: arranged freely by the players during the placement
 * phase, or by a preset that skips it. The presets are the lobby shortcuts for quick games; the free
 * placement is the real set-up of the game (the checkerboard is only one layout a player may choose).
 */
public enum MangolaSetup {
    FREE("free"), CHECKERBOARD("checkerboard"), RANDOM("random");

    public static final MangolaSetup DEFAULT = FREE;

    private final String key;

    MangolaSetup(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static MangolaSetup fromKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown set-up: " + key));
    }

    /** The board of a preset set-up; the free set-up has none, the players build it. */
    public MangolaBoard board(RandomGenerator random) {
        return switch (this) {
            case CHECKERBOARD -> checkerboard();
            case RANDOM -> random(random);
            case FREE -> throw new IllegalStateException("The free set-up has no preset board");
        };
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
