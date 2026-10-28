package org.kevinkib.cardgames.draughts.domain;

/**
 * The geometry of the 50 dark squares, numbered 1 to 50 left to right, row by row from the top row seen from
 * White's side (FMJD 2.6). Square n is on row (n-1)/5 (0 at the top) and column 2*((n-1)%5) + (row even ? 1 : 0).
 * Static tables built once; every other class reads them and nothing re-derives coordinates.
 */
public final class BoardGeometry {

    public static final int SQUARES = 50;
    public static final int SIZE = 10;

    /** The four diagonal directions, in a fixed order. */
    public enum Direction {
        NORTH_WEST(-1, -1), NORTH_EAST(-1, 1), SOUTH_WEST(1, -1), SOUTH_EAST(1, 1);

        private final int dRow;
        private final int dCol;

        Direction(int dRow, int dCol) {
            this.dRow = dRow;
            this.dCol = dCol;
        }
    }

    private static final int[] ROW = new int[SQUARES + 1];
    private static final int[] COL = new int[SQUARES + 1];
    private static final int[][] SQUARE_AT = new int[SIZE][SIZE];
    /** RAYS[square][direction.ordinal()] = the squares met going that way, nearest first. */
    private static final int[][][] RAYS = new int[SQUARES + 1][4][];

    static {
        for (int n = 1; n <= SQUARES; n++) {
            int r = (n - 1) / 5;
            int c = 2 * ((n - 1) % 5) + (r % 2 == 0 ? 1 : 0);
            ROW[n] = r;
            COL[n] = c;
            SQUARE_AT[r][c] = n;
        }
        for (int n = 1; n <= SQUARES; n++) {
            for (Direction d : Direction.values()) {
                int length = 0;
                int r = ROW[n] + d.dRow;
                int c = COL[n] + d.dCol;
                while (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                    length++;
                    r += d.dRow;
                    c += d.dCol;
                }
                int[] ray = new int[length];
                r = ROW[n] + d.dRow;
                c = COL[n] + d.dCol;
                for (int i = 0; i < length; i++) {
                    ray[i] = SQUARE_AT[r][c];
                    r += d.dRow;
                    c += d.dCol;
                }
                RAYS[n][d.ordinal()] = ray;
            }
        }
    }

    private BoardGeometry() {
    }

    public static int row(int square) {
        check(square);
        return ROW[square];
    }

    public static int col(int square) {
        check(square);
        return COL[square];
    }

    /** The square at (row, col), or 0 for a light square or a point outside the board. */
    public static int squareAt(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            return 0;
        }
        return SQUARE_AT[row][col];
    }

    /** The squares met going in {@code direction} from {@code square}, nearest first (a copy). */
    public static int[] ray(int square, Direction direction) {
        check(square);
        return RAYS[square][direction.ordinal()].clone();
    }

    /** The adjacent square in {@code direction}, or 0 at the edge. */
    public static int neighbour(int square, Direction direction) {
        check(square);
        int[] ray = RAYS[square][direction.ordinal()];
        return ray.length == 0 ? 0 : ray[0];
    }

    /** The row a man of {@code colour} must end its move on to be crowned: 0 for White, 9 for Black. */
    public static int promotionRow(Colour colour) {
        return colour == Colour.WHITE ? 0 : SIZE - 1;
    }

    public static boolean isPromotionSquare(Colour colour, int square) {
        return row(square) == promotionRow(colour);
    }

    private static void check(int square) {
        if (square < 1 || square > SQUARES) {
            throw new IllegalArgumentException("Square out of range: " + square);
        }
    }
}
