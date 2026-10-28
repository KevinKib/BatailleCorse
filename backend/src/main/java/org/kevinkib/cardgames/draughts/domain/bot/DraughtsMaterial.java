package org.kevinkib.cardgames.draughts.domain.bot;

import org.kevinkib.cardgames.draughts.domain.BoardGeometry;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.Piece;

/** Material counts shared by the levels: man 1, king 3. */
final class DraughtsMaterial {

    static final int MAN = 1;
    static final int KING = 3;

    private DraughtsMaterial() {
    }

    static int of(DraughtsPosition position, Colour colour) {
        int total = 0;
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null && piece.colour() == colour) {
                total += piece.isKing() ? KING : MAN;
            }
        }
        return total;
    }

    /** True when {@code me} is not ahead: the draw answer of a bot. */
    static boolean acceptsDraw(DraughtsPosition position, Colour me) {
        return of(position, me) <= of(position, me.opposite());
    }
}
