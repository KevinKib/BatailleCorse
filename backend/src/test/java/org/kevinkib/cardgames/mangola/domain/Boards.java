package org.kevinkib.cardgames.mangola.domain;

/** Test helper: boards and pit lists written with the pit names of the spec ({@code "a2:3 c2:1"}). */
final class Boards {

    private Boards() {
    }

    /** A board from "pit:seeds" tokens separated by spaces; pits not listed are empty. */
    static MangolaBoard of(String spec) {
        MangolaBoard board = MangolaBoard.empty();
        if (spec.isBlank()) {
            return board;
        }
        for (String token : spec.trim().split("\\s+")) {
            String[] parts = token.split(":");
            board = board.with(MangolaGeometry.pit(parts[0]), Integer.parseInt(parts[1]));
        }
        return board;
    }

    /** Both halves written in one string, e.g. {@code of("a2:2 c2:1", "c3:2 c4:3")}. */
    static MangolaBoard of(String sideA, String sideB) {
        return of(sideA + " " + sideB);
    }

    static int pit(String name) {
        return MangolaGeometry.pit(name);
    }
}
