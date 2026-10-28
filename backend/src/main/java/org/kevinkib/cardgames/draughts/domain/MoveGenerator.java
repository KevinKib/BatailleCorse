package org.kevinkib.cardgames.draughts.domain;

import java.util.List;

/**
 * Pure, stateless rules engine: legal moves of a position and the position after a move. The bodies belong to
 * chunk B1 ("draughts rules engine"); this shell only fixes the two public signatures.
 */
public class MoveGenerator {

    /** Every legal move for the colour to move, deterministic order (by from, to, captured squares). */
    public List<DraughtsMove> legalMoves(DraughtsPosition position) {
        throw new UnsupportedOperationException("Implemented by the rules engine chunk (B1)");
    }

    /** The position after {@code move}: pieces removed after the whole move, promotion, side to move swapped. */
    public DraughtsPosition apply(DraughtsPosition position, DraughtsMove move) {
        throw new UnsupportedOperationException("Implemented by the rules engine chunk (B1)");
    }
}
