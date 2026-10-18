package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

/**
 * Six Men's Morris positions written as 16 characters: points 0..7 are the outer square clockwise from the
 * top left (corners 0, 2, 4, 6), points 8..15 the inner square in the same order. W is seat 0, B is seat 1,
 * anything else is free.
 */
final class SixPositions {

    private SixPositions() {
    }

    static MorrisPosition of(String cells, int whiteInHand, int blackInHand, int toMove) {
        return Positions.of(MorrisRules.SIX_MEN, cells, whiteInHand, blackInHand, toMove);
    }

    /** The position where the seat to move must remove an opponent piece. */
    static MorrisPosition removing(String cells, int whiteInHand, int blackInHand, int toMove) {
        MorrisPosition position = of(cells, whiteInHand, blackInHand, toMove);
        return new MorrisPosition(position.rules(), position.board(), position.inHand(), toMove, true);
    }
}
