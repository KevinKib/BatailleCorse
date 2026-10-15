package org.kevinkib.cardgames.mangola.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The 4x8 board as constants. A pit is an index {@code row * 8 + column} (0..31), row 0 being the row
 * named "1" and column 0 the column named "a" (names {@code a1..h4}). Side A owns rows 1 and 2, side B
 * rows 3 and 4; rows 2 and 3 are the inner rows, facing each other. Each side sows along a closed loop
 * of its own 16 pits (the order of {@code plateau.js}).
 */
public final class MangolaGeometry {

    public static final int PITS = 32;
    public static final int COLUMNS = 8;
    public static final int LOOP_LENGTH = 16;
    /** Returned by {@link #facing} and {@link #behind} for an outer pit. */
    public static final int NONE = -1;

    private static final int[] NEXT = new int[PITS];
    private static final List<Integer> LOOP_A = buildLoop(1, 0);
    private static final List<Integer> LOOP_B = buildLoop(3, 2);

    static {
        link(LOOP_A);
        link(LOOP_B);
    }

    private MangolaGeometry() {
    }

    /** Inner row left to right, then the outer row right to left. */
    private static List<Integer> buildLoop(int innerRow, int outerRow) {
        List<Integer> pits = new ArrayList<>();
        for (int column = 0; column < COLUMNS; column++) {
            pits.add(innerRow * COLUMNS + column);
        }
        for (int column = COLUMNS - 1; column >= 0; column--) {
            pits.add(outerRow * COLUMNS + column);
        }
        return List.copyOf(pits);
    }

    private static void link(List<Integer> loop) {
        for (int i = 0; i < loop.size(); i++) {
            NEXT[loop.get(i)] = loop.get((i + 1) % loop.size());
        }
    }

    /** The 16 pits of a side in sowing order. */
    public static List<Integer> loop(Side side) {
        return side == Side.A ? LOOP_A : LOOP_B;
    }

    public static int next(int pit) {
        requirePit(pit);
        return NEXT[pit];
    }

    public static Side owner(int pit) {
        requirePit(pit);
        return pit / COLUMNS < 2 ? Side.A : Side.B;
    }

    public static boolean isInner(int pit) {
        requirePit(pit);
        int row = pit / COLUMNS;
        return row == 1 || row == 2;
    }

    /** The opponent's inner pit of the same column, {@link #NONE} for an outer pit. */
    public static int facing(int pit) {
        requirePit(pit);
        int row = pit / COLUMNS;
        return row == 1 ? pit + COLUMNS : row == 2 ? pit - COLUMNS : NONE;
    }

    /** The opponent's outer pit behind the facing pit, {@link #NONE} for an outer pit. */
    public static int behind(int pit) {
        requirePit(pit);
        int row = pit / COLUMNS;
        return row == 1 ? pit + 2 * COLUMNS : row == 2 ? pit - 2 * COLUMNS : NONE;
    }

    /** The name of a pit, {@code a1..h4}. */
    public static String name(int pit) {
        requirePit(pit);
        return "" + (char) ('a' + pit % COLUMNS) + (pit / COLUMNS + 1);
    }

    /** The pit of a name {@code a1..h4}. */
    public static int pit(String name) {
        if (name == null || name.length() != 2 || name.charAt(0) < 'a' || name.charAt(0) > 'h'
                || name.charAt(1) < '1' || name.charAt(1) > '4') {
            throw new IllegalArgumentException("No such pit: " + name);
        }
        return (name.charAt(1) - '1') * COLUMNS + (name.charAt(0) - 'a');
    }

    public static boolean isPit(int pit) {
        return pit >= 0 && pit < PITS;
    }

    private static void requirePit(int pit) {
        if (!isPit(pit)) {
            throw new IllegalArgumentException("No such pit: " + pit);
        }
    }
}
