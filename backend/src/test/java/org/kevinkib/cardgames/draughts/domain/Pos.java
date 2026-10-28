package org.kevinkib.cardgames.draughts.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Test helper: builds positions from specs such as {@code "w32"} (white man on 32) or {@code "B5"} (black king). */
final class Pos {

    private Pos() {
    }

    static DraughtsPosition of(Colour toMove, String... specs) {
        char[] layout = new char[BoardGeometry.SQUARES];
        Arrays.fill(layout, '.');
        for (String spec : specs) {
            layout[Integer.parseInt(spec.substring(1)) - 1] = spec.charAt(0);
        }
        return DraughtsPosition.fromLayout(new String(layout), toMove);
    }

    static DraughtsPosition white(String... specs) {
        return of(Colour.WHITE, specs);
    }

    static DraughtsPosition black(String... specs) {
        return of(Colour.BLACK, specs);
    }

    static List<String> notations(List<DraughtsMove> moves) {
        return moves.stream().map(DraughtsMove::notation).collect(Collectors.toList());
    }

    static DraughtsMove only(List<DraughtsMove> moves) {
        if (moves.size() != 1) {
            throw new AssertionError("Expected exactly one move, got " + notations(moves));
        }
        return moves.get(0);
    }
}
