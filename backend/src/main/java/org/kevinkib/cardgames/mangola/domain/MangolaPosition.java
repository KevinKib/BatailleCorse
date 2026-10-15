package org.kevinkib.cardgames.mangola.domain;

import java.util.List;

/**
 * An immutable snapshot of a game: the 32 pits, the seat to move and the rules. Used to count
 * repetitions and, later, by bots.
 */
public record MangolaPosition(MangolaRules rules, List<Integer> board, int toMove) {

    /** What identifies a position: the board and the seat to move. */
    public record Key(List<Integer> board, int toMove) {
    }

    public MangolaPosition {
        board = List.copyOf(board);
    }

    public Key key() {
        return new Key(board, toMove);
    }
}
