package org.kevinkib.cardgames.mangola.domain.bot;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement study only (test scope): a two-player zero-sum matrix game, {@code game[i][j]} being the row player's
 * expected score (win 1, draw 0.5, loss 0) when it plays row {@code i} and the column player plays column
 * {@code j}; the column player scores the complement, so it wants low values.
 */
final class ZeroSumGame {

    /**
     * An approximate equilibrium: each side's mix, the score the row mix guarantees against every column
     * ({@code lowerValue}) and the score the column mix concedes to the best row ({@code upperValue}). The value of
     * the game lies between them; their gap bounds how much either mix can be exploited.
     */
    record Solution(double[] rowMix, double[] columnMix, double lowerValue, double upperValue) {
        double gap() {
            return upperValue - lowerValue;
        }
    }

    private ZeroSumGame() {
    }

    /**
     * Alternating fictitious play (Brown 1951, Robinson's convergence theorem for zero-sum games): each side in
     * turn plays a best response to the other's empirical mix so far; the empirical mixes converge to an
     * equilibrium. Ties go to the lowest index, so the result is deterministic.
     */
    static Solution solve(double[][] game, int iterations) {
        int rows = game.length;
        int columns = game[0].length;
        double[] rowPayoff = new double[rows];     // sum over the column plays so far, for each row
        double[] columnPayoff = new double[columns]; // sum over the row plays so far, for each column
        long[] rowCounts = new long[rows];
        long[] columnCounts = new long[columns];
        for (int t = 0; t < iterations; t++) {
            int row = argMax(rowPayoff);
            rowCounts[row]++;
            for (int j = 0; j < columns; j++) {
                columnPayoff[j] += game[row][j];
            }
            int column = argMin(columnPayoff);
            columnCounts[column]++;
            for (int i = 0; i < rows; i++) {
                rowPayoff[i] += game[i][column];
            }
        }
        double[] rowMix = frequencies(rowCounts, iterations);
        double[] columnMix = frequencies(columnCounts, iterations);
        double lower = min(columnValuesAgainst(game, rowMix));
        double upper = max(rowValuesAgainst(game, columnMix));
        return new Solution(rowMix, columnMix, lower, upper);
    }

    /** The rows weakly dominated by another row: no better against any column, worse by more than the tolerance against one. */
    static List<Integer> dominatedRows(double[][] game, double tolerance) {
        List<Integer> dominated = new ArrayList<>();
        for (int i = 0; i < game.length; i++) {
            for (int k = 0; k < game.length; k++) {
                if (k != i && rowDominates(game, k, i, tolerance)) {
                    dominated.add(i);
                    break;
                }
            }
        }
        return dominated;
    }

    /** The columns weakly dominated for the column player (who wants low values). */
    static List<Integer> dominatedColumns(double[][] game, double tolerance) {
        return dominatedRows(negatedTranspose(game), tolerance);
    }

    /** The row whose worst column is the best (the safest pure choice). */
    static int maximinRow(double[][] game) {
        double[] worst = new double[game.length];
        for (int i = 0; i < game.length; i++) {
            worst[i] = min(game[i]);
        }
        return argMax(worst);
    }

    /** The row with the best mean against every column played equally often. */
    static int bestAgainstUniformRow(double[][] game) {
        double[] uniform = new double[game[0].length];
        java.util.Arrays.fill(uniform, 1.0 / uniform.length);
        return argMax(rowValuesAgainst(game, uniform));
    }

    /** The expected score of each row against a column mix. */
    static double[] rowValuesAgainst(double[][] game, double[] columnMix) {
        double[] values = new double[game.length];
        for (int i = 0; i < game.length; i++) {
            for (int j = 0; j < columnMix.length; j++) {
                values[i] += game[i][j] * columnMix[j];
            }
        }
        return values;
    }

    /** The expected row score of each column against a row mix. */
    static double[] columnValuesAgainst(double[][] game, double[] rowMix) {
        double[] values = new double[game[0].length];
        for (int i = 0; i < rowMix.length; i++) {
            for (int j = 0; j < values.length; j++) {
                values[j] += game[i][j] * rowMix[i];
            }
        }
        return values;
    }

    private static boolean rowDominates(double[][] game, int better, int worse, double tolerance) {
        boolean strictlySomewhere = false;
        for (int j = 0; j < game[better].length; j++) {
            if (game[better][j] < game[worse][j]) {
                return false;
            }
            if (game[better][j] > game[worse][j] + tolerance) {
                strictlySomewhere = true;
            }
        }
        return strictlySomewhere;
    }

    private static double[][] negatedTranspose(double[][] game) {
        double[][] transposed = new double[game[0].length][game.length];
        for (int i = 0; i < game.length; i++) {
            for (int j = 0; j < game[0].length; j++) {
                transposed[j][i] = -game[i][j];
            }
        }
        return transposed;
    }

    private static double[] frequencies(long[] counts, int total) {
        double[] mix = new double[counts.length];
        for (int i = 0; i < counts.length; i++) {
            mix[i] = (double) counts[i] / total;
        }
        return mix;
    }

    static int argMax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int argMin(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double min(double[] values) {
        return values[argMin(values)];
    }

    private static double max(double[] values) {
        return values[argMax(values)];
    }
}
