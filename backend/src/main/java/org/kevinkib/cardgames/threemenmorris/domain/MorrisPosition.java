package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of a game: the owner seat of each point (null when free), the pieces still
 * in hand per seat, the seat to move and the rules. Used to compare positions and, later, by bots.
 * {@code removing} is true when the seat to move must remove an opponent piece (it has just closed a mill).
 */
public record MorrisPosition(MorrisRules rules, List<Integer> board, List<Integer> inHand, int toMove,
                             boolean removing) {

    /** What identifies a position of the movement phase: the board and the seat to move. */
    public record Key(List<Integer> board, int toMove) {
    }

    public MorrisPosition(MorrisRules rules, List<Integer> board, List<Integer> inHand, int toMove) {
        this(rules, board, inHand, toMove, false);
    }

    public MorrisPosition {
        board = Collections.unmodifiableList(new ArrayList<>(board));
        inHand = List.copyOf(inHand);
    }

    public Key key() {
        return new Key(board, toMove);
    }
}
