package org.kevinkib.cardgames.draughts.domain;

import java.util.List;

/**
 * A legal move. {@code path} is the landing squares after {@code from}, ending with {@code to} (one entry for a
 * plain move); {@code captures} the captured squares in capture order; {@code promotes} is true for a man that
 * ends its move on its promotion row.
 */
public record DraughtsMove(int from, int to, List<Integer> path, List<Integer> captures, boolean promotes) {

    public DraughtsMove {
        path = List.copyOf(path);
        captures = List.copyOf(captures);
    }

    public static DraughtsMove plain(int from, int to) {
        return new DraughtsMove(from, to, List.of(to), List.of(), false);
    }

    public boolean isCapture() {
        return !captures.isEmpty();
    }

    /** FMJD notation: {@code from-to} for a plain move, {@code fromxto} for a capture (8.2). */
    public String notation() {
        return from + (isCapture() ? "x" : "-") + to;
    }
}
