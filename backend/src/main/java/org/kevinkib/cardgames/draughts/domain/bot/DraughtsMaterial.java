package org.kevinkib.cardgames.draughts.domain.bot;

import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;

/** Material counts shared by the levels: man 1, king 3. */
final class DraughtsMaterial {

    private DraughtsMaterial() {
    }

    static int of(DraughtsPosition position, Colour colour) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** True when {@code me} is not ahead: the draw answer of a bot. */
    static boolean acceptsDraw(DraughtsPosition position, Colour me) {
        throw new UnsupportedOperationException("not implemented");
    }
}
